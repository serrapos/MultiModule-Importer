package org.opencmshispano.multimoduleimporter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.PageContext;

import org.apache.commons.logging.Log;
import org.opencms.configuration.CmsConfigurationException;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.CmsRuntimeException;
import org.opencms.main.CmsSystemInfo;
import org.opencms.main.OpenCms;
import org.opencms.module.CmsModule;
import org.opencms.module.CmsModuleImportExportHandler;
import org.opencms.workplace.administration.A_CmsImportFromHttp;
import org.opencms.workplace.tools.CmsToolDialog;
import org.opencms.workplace.tools.CmsToolManager;
import org.opencms.workplace.tools.modules.CmsModulesList;
import org.opencmshispano.multimoduleimporter.util.Unzipper;

/**
 * Workplace tool dialog that provides support for multi-modules HTTP-uploads.
 * <p>
 * The dialog renders the browser's native file upload dialog, allowing the user to select a
 * "multi-package" zip bundle that will be uploaded to the server and unzipped.
 * <p>
 * Upon unzipping, the list of modules contained in the multi-package are read
 * and saved in the {@link CmsModulesMultiUploadFromHttp#SESSION_ATT_NAME_MODULES_LIST session}
 * for further processing by the {@link CmsModulesMultiUploadFromHttp#DIALOG_URI dialog jsp}.
 * <p>
 *
 * @author Sergio Raposo Vargas
 * @version $Revision: 1.0 $
 * @since 9.0.1
 */
public class CmsModulesMultiUploadFromHttp extends A_CmsImportFromHttp {

    /**
     * The dialog URI.
     * */
    public static final String DIALOG_URI = PATH_WORKPLACE + "admin/modules/modules_multi_import.jsp";

    public static final String SESSION_ATT_NAME_MODULES_LIST = "modulesToImport";

    /**
     * The log object for this class.
     * */
    private static final Log LOG = CmsLog.getLog(CmsModulesMultiUploadFromHttp.class);

    /**
     * HttpSession object.
     */
    private HttpSession session;

    /**
     * Public constructor with JSP action element.
     * <p>
     * 
     * @param jsp an initialized JSP action element
     */
    public CmsModulesMultiUploadFromHttp(final CmsJspActionElement jsp) {
        super(jsp);
    }

    /**
     * Public constructor with JSP variables.
     * <p>
     * 
     * @param context the JSP page context
     * @param req the JSP request
     * @param res the JSP response
     */
    public CmsModulesMultiUploadFromHttp(final PageContext context, final HttpServletRequest req, final HttpServletResponse res,
            final HttpSession session) {
        this(new CmsJspActionElement(context, req, res));
        this.session = session;
    }

    /**
     * @see org.opencms.workplace.administration.A_CmsImportFromHttp#actionCommit()
     */
    public void actionCommit() throws IOException, ServletException {

        // Subir el zip con los módulos y el xml.
        String filename = null;
        try {
            filename = copyFileToServer(OpenCms.getSystemInfo().getPackagesRfsPath() + File.separator
                    + CmsSystemInfo.FOLDER_MODULES);
        } catch (CmsException e) {
            // error copying the file to the OpenCms server
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getLocalizedMessage(getLocale()), e);
            }
            setException(e);
            return;
        }

        // Descomprimir el zip
        Unzipper.unzip(OpenCms.getSystemInfo().getPackagesRfsPath() + File.separator + CmsSystemInfo.FOLDER_MODULES
                + File.separator + filename);

        Enumeration zipEntries = Unzipper.getZipEntries(OpenCms.getSystemInfo().getPackagesRfsPath() + File.separator
                + CmsSystemInfo.FOLDER_MODULES + File.separator + filename);
        Map<String, CmsModule> modules = new HashMap<String,CmsModule>();
        List<String> zipElementNames = new ArrayList<String>();
        CmsConfigurationException exception = null;
        CmsModule module = null;

        while (zipEntries.hasMoreElements()) {
            zipElementNames.add(((ZipEntry) zipEntries.nextElement()).getName());
        }
        Iterator<String> it = zipElementNames.iterator();
        while (it.hasNext()) {
            try {
                String entryName = it.next();
                module = CmsModuleImportExportHandler.readModuleFromImport(OpenCms.getSystemInfo().getPackagesRfsPath()
                        + File.separator + CmsSystemInfo.FOLDER_MODULES + File.separator + entryName);
                modules.put(entryName, module);
            } catch (CmsConfigurationException e) {
                LOG.error(e.getMessage());
                exception = e;
            }
        }
        if (modules.isEmpty() || exception != null) {
            // log it
            if (LOG.isErrorEnabled()) {
                if (exception != null) {
                    LOG.error(exception.getLocalizedMessage(getLocale()), exception);
                } else {
                    LOG.error("Null modules");
                }
            } // then throw to avoid blank page telling nothing due to missing forward
            throw new CmsRuntimeException(exception.getMessageContainer(), exception);
        } else  {
            // refresh the list
            Map objects = (Map) getSettings().getListObject();
            if (objects != null) {
                objects.remove(CmsModulesList.class.getName());
            } // import / replace the modules Map
            Map param = new HashMap();
            param.put(CmsModulesList.PARAM_MODULE, getParamImportfile());
            param.put(PARAM_STYLE, CmsToolDialog.STYLE_NEW);
            File f = new File(OpenCms.getSystemInfo().getPackagesRfsPath() + File.separator + CmsSystemInfo.FOLDER_MODULES
                    + File.separator + filename);
            f.delete();
            param.put(PARAM_CLOSELINK, CmsToolManager.linkForToolPath(getJsp(), "/modules"));
            session.setAttribute(SESSION_ATT_NAME_MODULES_LIST, modules);
            getToolManager().jspForwardPage(this, CmsModulesListMultiReplaceReport.MULTI_IMPORT_ACTION_REPORT, param);
        }
    }

    /**
     * @see org.opencms.workplace.administration.A_CmsImportFromHttp#getDialogReturnUri()
     */
    public String getDialogReturnUri() {

        return DIALOG_URI;
    }

    /**
     * @see org.opencms.workplace.administration.A_CmsImportFromHttp#getImportMessage()
     */
    public String getImportMessage() {

        return key(org.opencms.workplace.tools.modules.Messages.GUI_MODULES_IMPORT_FILE_0);
    }

    /**
     * @see org.opencms.workplace.administration.A_CmsImportFromHttp#getStarttext()
     */
    public String getStarttext() {

        return key(org.opencms.workplace.tools.modules.Messages.GUI_MODULES_IMPORT_BLOCK_0);
    }

    /**
     * @see org.opencms.workplace.CmsWorkplace#initMessages()
     */
    protected void initMessages() {

        // add specific dialog resource bundle
        addMessages(org.opencms.workplace.tools.modules.Messages.get().getBundleName());
        // add default resource bundles
        addMessages(org.opencms.workplace.Messages.get().getBundleName());
        addMessages(org.opencms.workplace.tools.Messages.get().getBundleName());
    }
}
