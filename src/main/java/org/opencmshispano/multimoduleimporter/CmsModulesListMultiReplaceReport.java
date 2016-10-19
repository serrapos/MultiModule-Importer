package org.opencmshispano.multimoduleimporter;

import org.opencms.jsp.CmsJspActionElement;
import org.opencms.module.CmsModule;
import org.opencms.report.I_CmsReportThread;
import org.opencms.workplace.list.A_CmsListReport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.PageContext;
import java.util.Map;

/**
 * Provides a report for imporintg modules.
 * <p>
 * 
 * @author Sergio Raposo Vargas
 * 
 * @version $Revision: 1.0 $
 * 
 * @since 9.0.1
 */
public class CmsModulesListMultiReplaceReport extends A_CmsListReport {

    /**
     * Action page.
     */
    public static final String MULTI_IMPORT_ACTION_REPORT = "/system/workplace/admin/modules/reports/multi_import.jsp";
    /** Modules. */
    private Map<String, CmsModule> modules;

    /**
     * Public constructor with JSP action element.
     * <p>
     * 
     * @param jsp an initialized JSP action element
     */
    public CmsModulesListMultiReplaceReport(final CmsJspActionElement jsp) {
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
    public CmsModulesListMultiReplaceReport(final PageContext context, final HttpServletRequest req, final HttpServletResponse res, final HttpSession session) {
        this(new CmsJspActionElement(context, req, res));
        modules = (Map<String, CmsModule>) session.getAttribute(
                CmsModulesMultiUploadFromHttp.SESSION_ATT_NAME_MODULES_LIST);
    }

    /**
     * @see org.opencms.workplace.list.A_CmsListReport#initializeThread()
     */
    public I_CmsReportThread initializeThread() {

        I_CmsReportThread multiImportThread = new CmsModuleMultiImportThread(getCms(), modules);

        return multiImportThread;
    }

}
