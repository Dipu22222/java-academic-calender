package bd.ac.mbstu.calendar.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * SINGLETON pattern: only one DatabaseManager (and one connection) exists.
 *
 * - The constructor is private, so nobody can write "new DatabaseManager()".
 * - Everybody calls DatabaseManager.getInstance() and gets the same object.
 *
 * The SQLite file is created automatically at  data/calendar.db
 */
public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DEFAULT_URL =
            "jdbc:sqlite:" + AppPaths.dataDir().resolve("calendar.db").toString().replace('\\', '/');
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private static DatabaseManager instance;
    private static String configuredUrl = DEFAULT_URL;

    private final String url;
    private Connection connection;

    private DatabaseManager(String url) {
        this.url = url;
        openConnection();
        initSchema();
    }

    /** The one and only instance (created the first time it is needed). */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager(configuredUrl);
        }
        return instance;
    }

    /**
     * Switch to another database. Used by unit tests, for example
     * useUrl("jdbc:sqlite::memory:") gives a fresh empty database in RAM.
     */
    public static synchronized void useUrl(String newUrl) {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        configuredUrl = newUrl;
    }

    /** The shared connection. DAOs must NOT close it. */
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                openConnection();
            }
            return connection;
        } catch (SQLException e) {
            throw new DataAccessException("Cannot get database connection", e);
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                log.info("Database connection closed");
            }
        } catch (SQLException e) {
            log.warn("Error while closing database: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------

    private void openConnection() {
        try {
            if (url.startsWith(SQLITE_PREFIX) && !url.contains(":memory:")) {
                Path file = Paths.get(url.substring(SQLITE_PREFIX.length()));
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
            }
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            log.info("Connected to database {}", url);
        } catch (SQLException | IOException e) {
            throw new DataAccessException("Cannot open database " + url, e);
        }
    }

    /** Runs db/schema.sql (all statements use IF NOT EXISTS, so it is safe every start). */
    private void initSchema() {
        String sqlText = readResource("/db/schema.sql");

        // Remove "-- comments", then split into statements at ';'
        String cleaned = sqlText.lines()
                .map(line -> {
                    int idx = line.indexOf("--");
                    return idx >= 0 ? line.substring(0, idx) : line;
                })
                .collect(Collectors.joining("\n"));

        try (Statement st = connection.createStatement()) {
            for (String statement : cleaned.split(";")) {
                String sql = statement.trim();
                if (!sql.isEmpty()) {
                    st.execute(sql);
                }
            }
            log.info("Database schema is ready");
        } catch (SQLException e) {
            throw new DataAccessException("Schema setup failed", e);
        }
    }

    private String readResource(String path) {
        try (InputStream in = DatabaseManager.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new DataAccessException("Resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot read " + path, e);
        }
    }
}
