package bd.ac.mbstu.calendar.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads settings such as AI API keys WITHOUT hardcoding them in the source.
 * Lookup order for a key like "openai.api.key":
 *   1. Environment variable  OPENAI_API_KEY
 *   2. File  config/app.properties  (next to the app, NOT inside Git)
 *   3. The default value you pass in
 */
public final class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static final Path CONFIG_FILE = AppPaths.baseDir().resolve("config").resolve("app.properties");
    private static final Properties FILE_PROPS = new Properties();

    static {
        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                FILE_PROPS.load(in);
                log.info("Loaded settings from {}", CONFIG_FILE);
            } catch (IOException e) {
                log.warn("Could not read {}: {}", CONFIG_FILE, e.getMessage());
            }
        } else {
            log.info("No {} found. Using environment variables and defaults.", CONFIG_FILE);
        }
    }

    private Config() {
    }

    public static String get(String key, String defaultValue) {
        String envName = key.toUpperCase().replace('.', '_');
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = FILE_PROPS.getProperty(key);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
