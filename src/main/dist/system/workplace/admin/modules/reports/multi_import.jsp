<%@ page session="true" import="org.opencmshispano.multimoduleimporter.CmsModulesListMultiReplaceReport" %><%	

    CmsModulesListMultiReplaceReport wp = new CmsModulesListMultiReplaceReport(pageContext, request, response, session);
    wp.displayReport();
%>