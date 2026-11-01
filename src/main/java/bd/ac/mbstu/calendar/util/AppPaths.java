package bd.ac.mbstu.calendar.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Where the app keeps its files (data/calendar.db and config/app.properties).
 * Lookup order:
 *   1. system property  -Dapp.home=...    ({user.home} is replaced by your home folder)
 *   2. environment variable  MBSTU_CALENDAR_HOME
 *   3. the current folder (the same behaviour as before)
 */
public final class AppPaths {

    private static final Path BASE = resolveBase();

    private AppPaths() {
    }

    public static Path baseDir() {
        return BASE;
    }

    public static Path dataDir() {
        return BASE.resolve("data");
    }

    private static Path resolveBase() {
        String raw = System.getProperty("app.home");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("MBSTU_CALENDAR_HOME");
        }
        if (raw == null || raw.isBlank()) {
            return Paths.get("").toAbsolutePath();
        }
        raw = raw.trim().replace("{user.home}", System.getProperty("user.home", "."));
        return Paths.get(raw).toAbsolutePath().normalize();
    }
}
