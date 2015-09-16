package controlinterface.swinguiadvanced.constants;

import java.awt.Dimension;
import java.awt.Frame;

import framework.constants.Constants;

/**
 * Holds constants (static final values) of the Classes in package <code>swingadvanced</code>.
 * <p>
 * TODO: Move every constant here.
 *
 * @author Stefan Werner
 */
public class SwingAdvancedConstants {

	public static final String CI_ADVANCED_NAME = "SwingAdvanced";
	public static final String CI_SETUP_WIZARD_NAME = "Setup Wizard";
	public static final String CI_VERSION = "0.9beta";

	// Main Window
	public static final boolean USE_SEAGLASS_LNF = false;
	public static final int MAIN_WINDOW___DEFAULT_SIZE_X = 1100;
	public static final int MAIN_WINDOW___DEFAULT_SIZE_Y = 750;
	public static final int MAIN_WINDOW___DEFAULT_STATE = Frame.NORMAL;

	// LogPanel
	public static final String LOG_STYLE___HEADING = "heading";
	public static final String LOG_STYLE___DEFAULT = "def";
	public static final String LOG_STYLE___DEBUG = "debug";
	public static final String LOG_STYLE___INFO = "info";
	public static final String LOG_STYLE___WARNING = "warn";
	public static final String LOG_STYLE___ERROR = "err";
	public static final String LOG_STYLE___OWN_MSG = "own";
	public static final String ORIGIN___OWN_MESSAGE = "SELF";
	public static final int LOG___LINE_LIMIT = 500;
	public static final int LOG___FONT_SIZE = 12;
	public static final boolean LOG___SHOW_ERROR = true;
	public static final boolean LOG___SHOW_DEBUG = true;
	public static final boolean LOG___SHOW_INFO = true;
	public static final boolean LOG___SHOW_WARNING = true;
	public static final boolean LOG___SHOW_MODULE_ACTION = true;
	public static final boolean LOG___LINE_LIMIT_UNLIMITED = false;

	// GenericDialog
	public static final Dimension DIALOG___MIN_SIZE = new Dimension(300, 150);
	public static final Dimension DIALOG___DEFAULT_SIZE = new Dimension(400, 200);

	// //// Config
	public static final String CONFIG___DOMAIN = "config";
	// // Main Window
	public static final String[] CONFIG_PATH___MAIN_WINDOW = { "main_window" };
	// type -> int
	public static final String CONFIG___MAIN_WINDOW___STATE = "mw_state";
	// type -> int
	public static final String CONFIG___MAIN_WINDOW___SIZE_X = "mw_size_x";
	// type -> int
	public static final String CONFIG___MAIN_WINDOW___SIZE_Y = "mw_size_y";
	// type -> double
	public static final String CONFIG___MAIN_WINDOW___SPLITVIEW_VERT_DIV_POS = "sv_v_div";
	// type -> double
	public static final String CONFIG___MAIN_WINDOW___SPLITVIEW_HORIZ_DIV_POS = "sv_h_div";
	// // Graph
	public static final String[] CONFIG_PATH___CONNECTION_GRAPH_PANEL = { "conn_graph_panel" };
	// // LogPanel
	public static final String[] CONFIG_PATH___LOG_PANEL = { "log_panel" };
	// type -> int
	public static final String CONFIG___LOG_PANEL___FONT_SIZE = "s_mod_act";
	// type -> int
	public static final String CONFIG___LOG_PANEL___LINE_LIMIT = "l_limit";
	// type -> boolean
	public static final String CONFIG___LOG_PANEL___LINE_LIMIT_UNLIMITED = "ll_unlim";
	// type -> boolean
	public static final String CONFIG___LOG_PANEL___SHOW_ERROR = "s_err";
	public static final String CONFIG___LOG_PANEL___SHOW_DEBUG = "s_dbg";
	public static final String CONFIG___LOG_PANEL___SHOW_INFO = "s_info";
	public static final String CONFIG___LOG_PANEL___SHOW_WARNING = "s_warn";
	public static final String CONFIG___LOG_PANEL___SHOW_MODULE_ACTION = "s_mod_act";

	public static final String DEFAULT_EXPORT_DB_FILENAME = Constants.APP_NAME + "_" + Constants.APP_VERSION + "_db_export.json";
}