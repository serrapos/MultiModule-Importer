
package org.opencmshispano.multimoduleimporter;

import org.opencms.i18n.A_CmsMessageBundle;
import org.opencms.i18n.I_CmsMessageBundle;



/**
 * Clase que se usará para extraer los textos del fichero workplace.properties.
 */
public final class Messages extends A_CmsMessageBundle {

    /**
     * GUI_MULTIMODULE_ADMIN_TOOL_GROUP_0=Administración.
     */
    public static final String GUI_MULTIMODULE_ADMIN_TOOL_GROUP_0 = "GUI_MULTIMODULE_ADMIN_TOOL_GROUP_0";

    /**
     * GUI_MULTIIMPORTMODULE_ADMIN_TOOL_NAME_0=Importar varios módulos.
     */
    public static final String GUI_MULTIIMPORTMODULE_ADMIN_TOOL_NAME_0 = "GUI_MULTIIMPORTMODULE_ADMIN_TOOL_NAME_0";

    /**
     * Importar varios módulos desde un zip que los contenga.
     */
    public static final String GUI_MULTIIMPORTMODULE_ADMIN_TOOL_HELP_0 = "GUI_MULTIIMPORTMODULE_ADMIN_TOOL_HELP_0";

    /**
     * Nombre del recurso que contendrá los mensajes del modulo.
     */
    private static final String BUNDLE_NAME = "org.opencmshispano.multimoduleimporter";

    /**
     * Instancia del recurso.
     */
    private static final I_CmsMessageBundle INSTANCE = new Messages();

	public static final String ERR_ACTION_MODULE_DEPENDENCY_2 = "ERR_ACTION_MODULE_DEPENDENCY_2";

	public static final String ERR_ACTION_MODULE_UPLOAD_1 = "ERR_ACTION_MODULE_UPLOAD_1";

	public static final String GUI_MODULES_IMPORT_NOT_AVAILABLE_0 = "GUI_MODULES_IMPORT_NOT_AVAILABLE_0";

    /**
     * Constructor por defecto de la clase.
     */
    private Messages() {
    }

    /**
     * Método que devuelve una instancia del objeto.
     */
    public static I_CmsMessageBundle get() {
        return INSTANCE;
    }

    /**
     * Método que devuelve la cadena del recurso que contendrá las cadenas.
     */
    public String getBundleName() {
        return BUNDLE_NAME;
    }
}