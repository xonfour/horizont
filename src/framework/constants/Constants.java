package framework.constants;

import java.awt.Color;

/**
 * Contains important constants for the framework and the whole system.
 * <p>
 * TODO: Move every framework's constant here.
 *
 * @author Stefan Werner
 */
public class Constants {

	public static final String APP_NAME = "Horizont";
	public static final String APP_VERSION = "0.9 BETA";
	public static final String COMPONENT_ID___BROKER = "broker";
	public static final String COMPONENT_ID___CORE = "core";
	public static final String COMPONENT_ID___DATABASE = "database";
	public static final String COMPONENT_ID___LOCALISATION = "localization";
	public static final long CORE___ANNOUNCE_THREAD_TIMEOUT_SECONDS = 10;
	public static final String CORE___CONFIG_DOMAIN = "config";
	public static final String[] CORE___CONFIG_READY_ELEMENT = { "config_ready" };
	public static final String CORE___SESSION_ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	public static final int CORE___THREAD_POOL_SIZE = 5;
	public static final String CORE___UITYPE_SIMPLELOGGER = "simpleLogger";
	public static final String CORE___UITYPE_SWINGADVANCED = "swingAdvanced";
	public static final Color DARK_BG_COLOR = new Color(97, 129, 165);
	public static final String DEFAULT_CONFIG_LOCATION = "config/default_simple.json";
	public static final String DEFAULT_DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss Z";
	public static final String I18N_INTERNAL_RESOURCE_LOCATION = "localization.framework.framework";
	public static final int MAX_PATH_DEPTH = 32;
	// timeout for control interface management calls (start, stop etc.)
	public static final long TIMEOUT_SECONDS___CI_MANAGEMENT = 10;
	// timeout for command calls (module to module / control interface to module)
	public static final long TIMEOUT_SECONDS___MODULE_COMMUNICATION = 30;
	// timeout for module management calls (start, stop etc.)
	public static final long TIMEOUT_SECONDS___MODULE_MANAGEMENT = 10;
}
