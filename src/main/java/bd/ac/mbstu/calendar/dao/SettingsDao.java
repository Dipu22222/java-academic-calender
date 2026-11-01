package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Saves small settings such as theme ("light"/"dark") and language ("en"/"bn"). */
public class SettingsDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public String get(String key, String defaultValue) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT value FROM app_settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : defaultValue;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read setting " + key, e);
        }
    }

    public void set(String key, String value) {
        String sql = "INSERT INTO app_settings (key, value) VALUES (?, ?) "
                   + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save setting " + key, e);
        }
    }
}
