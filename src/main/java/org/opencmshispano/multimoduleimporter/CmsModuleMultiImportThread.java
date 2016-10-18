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
 * 
 * @version $Revision: 1.0 $
 * 
 * @since 9.0.1
 */
public class CmsModuleMultiImportThread extends A_CmsReportThread {

    /**
     * The log object for this class.
     * */
    private static final Log LOG = CmsLog.getLog(CmsModuleMultiImportThread.class);

    /**
     * Referencia a CmsModuleManager.
     */
    private static CmsModuleManager moduleManager = OpenCms.getModuleManager();

    /** Unsorted list of all filename -> module to be imported */
    private final Map<String, CmsModule> modules;

    /**
     * Hilo encargado de eliminar los módulos ya instalados.
     */
    private A_CmsReportThread deleteThread;
    /**
     * List of modules to install sorted according to their declared dependencies
     */
    private List xxmoduleNames;
    /**
     * Fase por la que se encuentra el proceso.
     */
    private int phase;
    /**
     * Contenido del informe del proceso.
     */
    private String reportContent;
    /**
     * Ruta del fichero zip que contiene el conjunto de módulos.
     */
    private final String importPath;

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
     * Collects all resource names belonging to a module in a Vector.
     * <p>
     * 
     * @param moduleName the name of the module
     * 
     * @return Vector with path Strings of resources
     */
    public static Vector getModuleResources(final String moduleName) {

        Vector resNames = new Vector(moduleManager.getModule(moduleName).getResources());
        return resNames;
    }

    /**
     * @see org.opencms.report.A_CmsReportThread#getReportUpdate()
     */
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

    /**
     * @see Runnable#run()
     */
    public void run() {

        if (LOG.isDebugEnabled()) {
            LOG.debug(Messages.get().getBundle().key(
                    Messages.LOG_REPLACE_THREAD_START_DELETE_0));
        }

        List<String> installedModules = new ArrayList<String>();
        for (Map.Entry<String, CmsModule> e: modules.entrySet()) {
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
        deleteThread = new CmsModuleDeleteThread(getCms(), installedModules, true);

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

        // module name -> filename lookup table
        Map<String,String> moduleNamesFilenames = new HashMap<String, String>(modules.size());
        for (Map.Entry<String,CmsModule> e: modules.entrySet()) {
            moduleNamesFilenames.put(e.getValue().getName(), e.getKey());
        }

        // (Yet) unsorted list of all module names
        List<String> moduleNames = new ArrayList<String>(modules.size());
        for (Map.Entry<String, CmsModule> e: modules.entrySet()) {
            CmsModule m = e.getValue();
            moduleNames.add(m.getName());
        }

        try {
            moduleNames = CmsModuleManager.topologicalSort(moduleNames, importPath);
        } catch (CmsConfigurationException e) {
            // We'll have to deal with the unsorted list of modules
            LOG.warn("Error sorting list of modules topologically: " + e.getMessage(), e);
        }

        // Nested loop: if moduleNames could not be sorted topologically, use
        // brute-force iterating through all modules as many times as needed
        int lastSize = Integer.MAX_VALUE;
        int curSize = moduleNames.size();
        boolean finished = false;
        while (!finished) {
            finished = !(curSize < lastSize);
            lastSize = moduleNames.size();
            Iterator<String> itModuleNames = moduleNames.iterator();
            while (itModuleNames.hasNext()) {
                String moduleName = itModuleNames.next();
                String moduleFilename = moduleNamesFilenames.get(moduleName);
                if (importModule(moduleFilename, finished)) {
                    itModuleNames.remove();
                }
            }
            curSize = moduleNames.size();
        }
    }

    /**
     * 
     * @param moduleFilename The filename of the module to import
     * @param finished
     * @return true if module has been imported
     */
    private boolean importModule(final String moduleFilename, final boolean finished) {
        CmsModule m = moduleManager.getModule(moduleFilename);
        if (m == null) {
            CmsImportParameters parameters = new CmsImportParameters(importPath + File.separator + moduleFilename, "/", true);
            try {
                OpenCms.getImportExportManager().importData(getCms(), getReport(), parameters);
            } catch (Throwable e) {
                if (finished) {
                    getReport().println(e);
                    if (LOG.isErrorEnabled()) {
                        LOG.error(Messages.get().getBundle().key(Messages.ERR_DB_IMPORT_0), e);
                    }
                }
                return false;
            }
            return true;
        } else {
            return false;
        }
    }
}