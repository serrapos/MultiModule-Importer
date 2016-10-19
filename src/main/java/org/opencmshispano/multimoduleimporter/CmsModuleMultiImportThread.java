package org.opencmshispano.multimoduleimporter;

import org.apache.commons.logging.Log;
import org.opencms.configuration.CmsConfigurationException;
import org.opencms.file.CmsObject;
import org.opencms.importexport.CmsImportParameters;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.module.CmsModule;
import org.opencms.module.CmsModuleManager;
import org.opencms.report.A_CmsReportThread;
import org.opencms.workplace.threads.CmsModuleDeleteThread;
import org.opencms.workplace.threads.Messages;

import java.io.File;
import java.util.*;

/**
 * Replaces a module.
 * <p>
 *
 * @author Sergio Raposo Vargas
 * @version $Revision: 1.0 $
 * @since 9.0.1
 */
public class CmsModuleMultiImportThread extends A_CmsReportThread {

    /**
     * The log object for this class.
     */
    private static final Log LOG = CmsLog.getLog(CmsModuleMultiImportThread.class);

    /**
     * Referencia a CmsModuleManager.
     */
    private static CmsModuleManager moduleManager = OpenCms.getModuleManager();

    /**
     * Unsorted list of all filename -> module to be imported
     */
    private final Map<String, CmsModule> modules;

    /**
     * Ruta del fichero zip que contiene el conjunto de módulos.
     */
    private final String importPath;

    /**
     * Hilo encargado de eliminar los módulos ya instalados.
     */
    private A_CmsReportThread deleteThread;

    /**
     * Fase por la que se encuentra el proceso.
     */
    private int phase;

    /**
     * Contenido del informe del proceso.
     */
    private String reportContent;

    /**
     * Creates the module replace thread and sorts the list of modules to be imported considering the declared
     * dependencies
     * <p>
     *
     * @param cms     the current cms context
     * @param modules filename -> cmsmodule
     */
    public CmsModuleMultiImportThread(final CmsObject cms, final Map<String, CmsModule> modules) {

        super(cms, org.opencms.workplace.threads.Messages.get().getBundle().key(
                org.opencms.workplace.threads.Messages.GUI_DELETE_MODULE_THREAD_NAME_1));

        this.importPath = OpenCms.getSystemInfo().getPackagesRfsPath() + File.separator + "modules/";
        this.modules = modules;
        phase = 0;

        initHtmlReport(cms.getRequestContext().getLocale());
    }

    /**
     * @return module name -> filename lookup table
     */
    private static Map<String, String> createNameFilenameLookupTable(Map<String, CmsModule> modules) {
        Map<String, String> moduleNamesFilenames = new HashMap<String, String>(modules.size());
        for (Map.Entry<String, CmsModule> e : modules.entrySet()) {
            moduleNamesFilenames.put(e.getValue().getName(), e.getKey());
        }
        return moduleNamesFilenames;
    }

    /**
     * @return list of modules topologically sorted if possible
     */
    private static List<String> createModulesList(Map<String, CmsModule> modules, String importPath) {
        // (Yet) unsorted list of all module names
        List<String> moduleNames = new ArrayList<String>(modules.size());
        for (Map.Entry<String, CmsModule> e : modules.entrySet()) {
            CmsModule m = e.getValue();
            moduleNames.add(m.getName());
        }

        try {
            moduleNames = CmsModuleManager.topologicalSort(moduleNames, importPath);
        } catch (CmsConfigurationException e) {
            // We'll have to deal with the unsorted list of modules
            LOG.warn("Error sorting list of modules topologically: " + e.getMessage(), e);
        }
        return moduleNames;
    }

    /**
     * @see org.opencms.report.A_CmsReportThread#getReportUpdate()
     */
    @Override
    public String getReportUpdate() {

        switch (phase) {
            case 1:
                return deleteThread.getReportUpdate();
            case 2:
                return getReport().getReportUpdate();
            default:
                // noop
        }
        return "";
    }

    @Override
    public void run() {
        if (LOG.isDebugEnabled()) {
            LOG.debug(Messages.get().getBundle().key(Messages.LOG_REPLACE_THREAD_START_DELETE_0));
        }

        deleteThread = createDeleteThread(modules, moduleManager);

        // phase 1: delete the existing module
        phase = 1;
        deleteThread.start();
        try {
            deleteThread.join();
        } catch (InterruptedException e) {
            // should never happen
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getLocalizedMessage(), e);
            }
        }

        // get remaining report contents
        reportContent = deleteThread.getReportUpdate();
        if (LOG.isDebugEnabled()) {
            LOG.debug(Messages.get().getBundle().key(
                    Messages.LOG_REPLACE_THREAD_START_IMPORT_0));
        }

        // phase 2: import the new modules
        phase = 2;
        Map<String, String> moduleNamesFilenames = createNameFilenameLookupTable(modules);
        List<String> moduleNames = createModulesList(modules, importPath);
        importModules(moduleNames, moduleNamesFilenames);
    }

    /**
     * @param modules       list of modules and their filenames to be deleted
     * @param moduleManager
     * @return a thread that in its run method will remove <code>modules</code> from OpenCms
     */
    private A_CmsReportThread createDeleteThread(Map<String, CmsModule> modules, CmsModuleManager moduleManager) {
        List<String> installedModules = new ArrayList<String>();
        for (Map.Entry<String, CmsModule> e : modules.entrySet()) {
            CmsModule m = e.getValue();
            // Module exists already and must be deleted
            if (null != moduleManager.getModule(m.getName())) {
                LOG.trace(String.format("Found module for file \"%s with name \"%s. Recording it for deletion.",
                        e.getKey(), m.getName()));
                installedModules.add(m.getName());
            } else {
                LOG.trace(String.format("Module for file \"%s with name \"%s not yet installed.",
                        e.getKey(), m.getName()));
            }
        }
        return new CmsModuleDeleteThread(getCms(), installedModules, true);
    }

    /**
     * Import all named modules. This method installs each of the modules in <code>moduleNames</code>, if necessary
     * walking through the list multiple times until all are installed.
     *
     * @param moduleNames          to import. If possible, this list should be sorted topologically
     *                             (see {@link CmsModuleManager#topologicalSort(List, String)})
     * @param moduleNamesFilenames <code>moduleName -> filename</code> lookup table used to find the filenames of the modules
     */
    private void importModules(List<String> moduleNames, Map<String, String> moduleNamesFilenames) {
        // For debugging: count of passes through the list
        int passes = 0;

        // Iterate through the modules list as many times as necessary until no more modules can be installed.
        // Successfully installed modules are removed from the list
        int lastSize = Integer.MAX_VALUE;
        int curSize = moduleNames.size();
        boolean lastPass = false;
        while (!lastPass) {
            lastPass = (curSize == lastSize); // The previous pass didn't import any package
            lastSize = moduleNames.size();

            // Next pass through the modules list
            LOG.debug("Import modules - Pass " + passes + ". Yet " + lastSize + " modules to try to install");
            Iterator<String> itModuleNames = moduleNames.iterator();
            while (itModuleNames.hasNext()) {
                String moduleName = itModuleNames.next();
                String moduleFilename = moduleNamesFilenames.get(moduleName);
                try {
                    boolean importedSuccessfully = importModule(moduleName, moduleFilename);
                    if (importedSuccessfully) {
                        itModuleNames.remove();
                    }
                } catch (Exception e) {
                    if (lastPass) {
                        // Only report errors during import as errors in the last pass (the module cannot be installed)
                        getReport().println(e);
                        LOG.error(Messages.get().getBundle().key(Messages.ERR_DB_IMPORT_0), e);
                    } else {
                        LOG.debug(String.format("Import modules - Pass %d. Cannot import \"%s\" from %s: %s",
                                passes, moduleName, moduleFilename, e.getLocalizedMessage()), e);
                    }
                }
            }
            curSize = moduleNames.size();
            passes++;
        }
    }

    /**
     * @param moduleName     Name of the module to import (e.g. <code>my.module</code>)
     * @param moduleFilename The filename of the module to import (e.g. <code>my.module-1.0.1.zip</code>)
     * @return <code>true</code> if module has been imported; <code>false</code> otherwise (module already installed
     * or exception during module import)
     * @throws Exception upon error during module import
     */
    private boolean importModule(String moduleName, final String moduleFilename) throws Exception {
        CmsModule m = moduleManager.getModule(moduleName);
        if (m != null) {
            LOG.warn(String.format("Import module - Skipping module \"%s\" (%s): already installed!",
                    moduleName, moduleFilename));
            return false;
        }
        CmsImportParameters parameters = new CmsImportParameters(
                importPath + File.separator + moduleFilename, "/", true);

        OpenCms.getImportExportManager().importData(getCms(), getReport(), parameters);
        return true;
    }
}