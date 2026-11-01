package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Remembers which reminders were already shown, so they never pop up twice. */
public class ReminderLogDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public boolean wasShown(long eventId, long userId, int minutesBefore) {
        String sql = "SELECT 1 FROM reminder_log WHERE event_id = ? AND user_id = ? AND minutes_before = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setLong(2, userId);
            ps.setInt(3, minutesBefore);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read reminder log", e);
        }
    }

    public void markShown(long eventId, long userId, int minutesBefore) {
        String sql = "INSERT OR IGNORE INTO reminder_log (event_id, user_id, minutes_before) VALUES (?,?,?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, eventId);
            ps.setLong(2, userId);
            ps.setInt(3, minutesBefore);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not write reminder log", e);
        }
    }
}
