<%@ page session="true" import="org.opencmshispano.multimoduleimporter.CmsModulesMultiUploadFromHttp" %>
<%
    CmsModulesMultiUploadFromHttp wp = new CmsModulesMultiUploadFromHttp(pageContext, request, response, session);
    wp.displayDialog();
%>