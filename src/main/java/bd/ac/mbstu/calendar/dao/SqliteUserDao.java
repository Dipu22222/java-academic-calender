package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.DatabaseManager;
import bd.ac.mbstu.calendar.util.JdbcUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** UserDao implemented with SQLite. */
public class SqliteUserDao implements UserDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    @Override
    public User insert(User user) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role, "
                   + "department_id, year, semester) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole().name());
            JdbcUtil.setNullableLong(ps, 5, user.getDepartmentId());
            JdbcUtil.setNullableInt(ps, 6, user.getYear());
            JdbcUtil.setNullableInt(ps, 7, user.getSemester());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            return user;
        } catch (SQLException e) {
            throw new DataAccessException("Could not insert user " + user.getUsername(), e);
        }
    }

    @Override
    public Optional<User> findByUsername(String username) {
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not find user " + username, e);
        }
    }

    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM users ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list users", e);
        }
        return users;
    }

    @Override
    public long count() {
        try (PreparedStatement ps = conn().prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not count users", e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                Role.fromString(rs.getString("role")),
                JdbcUtil.getNullableLong(rs, "department_id"),
                JdbcUtil.getNullableInt(rs, "year"),
                JdbcUtil.getNullableInt(rs, "semester"));
    }
}
