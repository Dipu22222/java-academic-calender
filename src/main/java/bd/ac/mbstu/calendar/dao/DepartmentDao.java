package bd.ac.mbstu.calendar.dao;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads departments (ICT, CSE, EEE...). */
public class DepartmentDao {

    private Connection conn() {
        return DatabaseManager.getInstance().getConnection();
    }

    public List<Department> findAll() {
        List<Department> list = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT * FROM departments ORDER BY code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list departments", e);
        }
        return list;
    }

    public Optional<Department> findByCode(String code) {
        return findOne("SELECT * FROM departments WHERE UPPER(code) = UPPER(?)", code);
    }

    public Optional<Department> findById(long id) {
        return findOne("SELECT * FROM departments WHERE id = ?", id);
    }

    private Optional<Department> findOne(String sql, Object param) {
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Department lookup failed", e);
        }
    }

    private Department mapRow(ResultSet rs) throws SQLException {
        return new Department(rs.getLong("id"), rs.getString("code"),
                rs.getString("name_en"), rs.getString("name_bn"));
    }
}
