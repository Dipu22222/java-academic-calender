package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.DatabaseManager;
import bd.ac.mbstu.calendar.util.JdbcUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** EventDao implemented with SQLite and JDBC. */
public class SqliteEventDao implements EventDao {

    // Column order used by both INSERT and UPDATE (so one bind method serves both)
    private static final String INSERT_SQL =
            "INSERT INTO events (title, title_bn, type, start_date, end_date, start_time, end_time, "
          + "department_id, year, semester, course_code, description, location, reminder_minutes, "
          + "class_off, office_off, owner_user_id, created_by) "
          + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String UPDATE_SQL =
            "UPDATE events SET title=?, title_bn=?, type=?, start_date=?, end_date=?, start_time=?, "
          + "end_time=?, department_id=?, year=?, semester=?, course_code=?, description=?, location=?, "
          + "reminder_minutes=?, class_off=?, office_off=?, owner_user_id=?, created_by=?, "
          + "updated_at=datetime('now') WHERE id=?";

    private static final String ORDER_BY = " ORDER BY start_date, IFNULL(start_time,''), title";

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    @Override
    public Event insert(Event event) {
        try (PreparedStatement ps = conn().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, event);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    event.setId(keys.getLong(1));
                }
            }
            return event;
        } catch (SQLException e) {
            throw new DataAccessException("Could not insert event: " + event.getTitle(), e);
        }
    }

    @Override
    public void update(Event event) {
        try (PreparedStatement ps = conn().prepareStatement(UPDATE_SQL)) {
            bind(ps, event);
            ps.setLong(19, event.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update event " + event.getId(), e);
        }
    }

    @Override
    public void delete(long id) {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM events WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete event " + id, e);
        }
    }

    @Override
    public Optional<Event> findById(long id) {
        List<Event> list = query("SELECT * FROM events WHERE id = ?", List.of(id));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<Event> findAll() {
        return query("SELECT * FROM events" + ORDER_BY, List.of());
    }

    @Override
    public List<Event> findByFilter(EventFilter f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM events WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // Official events are visible to all. Personal events only to their owner.
        if (f.getViewerUserId() != null) {
            sql.append(" AND (owner_user_id IS NULL OR owner_user_id = ?)");
            params.add(f.getViewerUserId());
        } else {
            sql.append(" AND owner_user_id IS NULL");
        }
        if (f.getFrom() != null) {
            sql.append(" AND end_date >= ?");
            params.add(f.getFrom().toString());
        }
        if (f.getTo() != null) {
            sql.append(" AND start_date <= ?");
            params.add(f.getTo().toString());
        }
        if (f.getType() != null) {
            sql.append(" AND type = ?");
            params.add(f.getType().name());
        }
        if (f.getDepartmentId() != null) {
            sql.append(" AND (department_id IS NULL OR department_id = ?)");
            params.add(f.getDepartmentId());
        }
        if (f.getYear() != null) {
            sql.append(" AND (year IS NULL OR year = ?)");
            params.add(f.getYear());
        }
        if (f.getSemester() != null) {
            sql.append(" AND (semester IS NULL OR semester = ?)");
            params.add(f.getSemester());
        }
        if (f.getKeyword() != null && !f.getKeyword().isBlank()) {
            String like = "%" + f.getKeyword().trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(IFNULL(title_bn,'')) LIKE ?"
                     + " OR LOWER(IFNULL(description,'')) LIKE ? OR LOWER(IFNULL(course_code,'')) LIKE ?"
                     + " OR LOWER(IFNULL(location,'')) LIKE ?)");
            for (int i = 0; i < 5; i++) {
                params.add(like);
            }
        }
        sql.append(ORDER_BY);
        return query(sql.toString(), params);
    }

    @Override
    public boolean existsByTitleAndStart(String title, LocalDate startDate) {
        List<Event> found = query(
                "SELECT * FROM events WHERE title = ? AND start_date = ?",
                List.of(title, startDate.toString()));
        return !found.isEmpty();
    }

    @Override
    public long count() {
        try (PreparedStatement ps = conn().prepareStatement("SELECT COUNT(*) FROM events");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not count events", e);
        }
    }

    // ---------------------------------------------------------------
    // private helpers
    // ---------------------------------------------------------------

    /** Runs a SELECT with the given parameters and converts every row to an Event. */
    private List<Event> query(String sql, List<Object> params) {
        List<Event> result = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Event query failed: " + sql, e);
        }
        return result;
    }

    /** Fills the 18 "?" placeholders (positions 1..18) from an Event. */
    private void bind(PreparedStatement ps, Event e) throws SQLException {
        ps.setString(1, e.getTitle());
        ps.setString(2, e.getTitleBn());
        ps.setString(3, e.getType().name());
        ps.setString(4, e.getStartDate().toString());
        ps.setString(5, e.getEndDate().toString());
        ps.setString(6, e.getStartTime() == null ? null : e.getStartTime().toString());
        ps.setString(7, e.getEndTime() == null ? null : e.getEndTime().toString());
        JdbcUtil.setNullableInt(ps, 8, e.getDepartmentId());
        JdbcUtil.setNullableInt(ps, 9, e.getYear());
        JdbcUtil.setNullableInt(ps, 10, e.getSemester());
        ps.setString(11, e.getCourseCode());
        ps.setString(12, e.getDescription());
        ps.setString(13, e.getLocation());
        JdbcUtil.setNullableInt(ps, 14, e.getReminderMinutes());
        ps.setInt(15, e.isClassOff() ? 1 : 0);
        ps.setInt(16, e.isOfficeOff() ? 1 : 0);
        JdbcUtil.setNullableLong(ps, 17, e.getOwnerUserId());
        JdbcUtil.setNullableLong(ps, 18, e.getCreatedBy());
    }

    /** Converts the current database row into an Event object. */
    private Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getLong("id"));
        e.setTitle(rs.getString("title"));
        e.setTitleBn(rs.getString("title_bn"));
        e.setType(EventType.valueOf(rs.getString("type")));
        e.setStartDate(LocalDate.parse(rs.getString("start_date")));
        e.setEndDate(LocalDate.parse(rs.getString("end_date")));
        String st = rs.getString("start_time");
        e.setStartTime(st == null ? null : LocalTime.parse(st));
        String et = rs.getString("end_time");
        e.setEndTime(et == null ? null : LocalTime.parse(et));
        e.setDepartmentId(JdbcUtil.getNullableInt(rs, "department_id"));
        e.setYear(JdbcUtil.getNullableInt(rs, "year"));
        e.setSemester(JdbcUtil.getNullableInt(rs, "semester"));
        e.setCourseCode(rs.getString("course_code"));
        e.setDescription(rs.getString("description"));
        e.setLocation(rs.getString("location"));
        e.setReminderMinutes(JdbcUtil.getNullableInt(rs, "reminder_minutes"));
        e.setClassOff(rs.getInt("class_off") == 1);
        e.setOfficeOff(rs.getInt("office_off") == 1);
        e.setOwnerUserId(JdbcUtil.getNullableLong(rs, "owner_user_id"));
        e.setCreatedBy(JdbcUtil.getNullableLong(rs, "created_by"));
        return e;
    }
}
