package org.opencmshispano.multimoduleimporter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

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

    /**
     * Hilo encargado de eliminar los módulos ya instalados.
     */
    private A_CmsReportThread deleteThread;
    /**
     * Lista de módulos a instalar.
     */
    private List<CmsModule> modules;
    /**
     * Lista de módulos a instalar.
     */
    private Map<String,String> modulesZip;
    /**
     * Lista con los nombres de los módulos a instalar.
     */
    private List moduleNames;
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
    private String importPath;

    /**
     * Creates the module replace thread.
     * <p>
     * 
     * @param cms the current cms context
     * @param moduleName the name of the module
     * @param zipName the name of the module ZIP file
     */
    public CmsModuleMultiImportThread(final CmsObject cms, final List<CmsModule> modules) {

        super(cms, org.opencms.workplace.threads.Messages.get().getBundle().key(
                org.opencms.workplace.threads.Messages.GUI_DELETE_MODULE_THREAD_NAME_1));

        this.importPath = OpenCms.getSystemInfo().getPackagesRfsPath() + File.separator + "modules/";
        this.modules = modules;
        moduleNames = new ArrayList<String>();
        Iterator<CmsModule> it = modules.iterator();
        
        modulesZip = new HashMap<String,String>();
        while (it.hasNext()) {
        	CmsModule m = (CmsModule)it.next();
            moduleNames.add(m.getName());
            modulesZip.put(m.getName(),m.getVersion().getVersion());
        }

        CmsModuleManager manager = moduleManager;
        CmsConfigurationException exception = null;
        it = modules.iterator();
        List<String> modulesToDelete = new ArrayList<String>();
        while (it.hasNext()) {
            CmsModule module = it.next();
            if (moduleManager.getModule(module.getName()) != null) {
                modulesToDelete.add(module.getName());
            }
        }
        deleteThread = new CmsModuleDeleteThread(getCms(), modulesToDelete, true);
        try {
            moduleNames = CmsModuleManager.topologicalSort(moduleNames, importPath);
        } catch (CmsConfigurationException e) {
            LOG.error(e.getMessage());
        }

        initHtmlReport(cms.getRequestContext().getLocale());

        phase = 0;
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
     * @see java.lang.Runnable#run()
     */
    public void run() {

        if (LOG.isDebugEnabled()) {
            LOG.debug(org.opencms.workplace.threads.Messages.get().getBundle().key(
                    org.opencms.workplace.threads.Messages.LOG_REPLACE_THREAD_START_DELETE_0));
        }
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
            LOG.debug(org.opencms.workplace.threads.Messages.get().getBundle().key(
                    org.opencms.workplace.threads.Messages.LOG_REPLACE_THREAD_START_IMPORT_0));
        }
        // phase 2: import the new modules
        phase = 2;

        int lastSize = Integer.MAX_VALUE;
        int curSize = moduleNames.size();
        boolean finished = false;
        Iterator itModuleNames = null;
        while (!finished) {
            finished = !(curSize < lastSize);
            lastSize = moduleNames.size();
            itModuleNames = moduleNames.iterator();
            while (itModuleNames.hasNext()) {
                String moduleName = (String) itModuleNames.next();
                if (importModule(moduleName, finished)) {
                    itModuleNames.remove();
                }
            }
            curSize = moduleNames.size();
        }
    }

    /**
     * 
     * @param moduleName The name of the module to import
     * @param finished
     * @return true if module has been imported
     */
    private boolean importModule(final String moduleName, final boolean finished) {
        CmsModule m = moduleManager.getModule(moduleName);
        if (m == null) {
        	String version = modulesZip.get(moduleName);
            CmsImportParameters parameters = new CmsImportParameters(importPath + File.separator + moduleName + "_"+ version + ".zip", "/", true);
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