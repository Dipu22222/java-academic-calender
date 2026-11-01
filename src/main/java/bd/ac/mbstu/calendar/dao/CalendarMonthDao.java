package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.model.CalendarMonth;
import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Stores and reads the Bangla / Hijri month names for each English month. */
public class CalendarMonthDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    /** Insert, or replace if that year+month already exists ("upsert"). */
    public void save(CalendarMonth m) {
        String sql = "INSERT INTO calendar_months (year, month, bangla_months, hijri_months) "
                   + "VALUES (?,?,?,?) "
                   + "ON CONFLICT(year, month) DO UPDATE SET "
                   + "bangla_months = excluded.bangla_months, hijri_months = excluded.hijri_months";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, m.year());
            ps.setInt(2, m.month());
            ps.setString(3, m.banglaMonths());
            ps.setString(4, m.hijriMonths());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save calendar month", e);
        }
    }

    public Optional<CalendarMonth> find(int year, int month) {
        String sql = "SELECT * FROM calendar_months WHERE year = ? AND month = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new CalendarMonth(rs.getInt("year"), rs.getInt("month"),
                            rs.getString("bangla_months"), rs.getString("hijri_months")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read calendar month", e);
        }
    }
}
