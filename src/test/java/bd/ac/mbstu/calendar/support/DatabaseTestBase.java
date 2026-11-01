package bd.ac.mbstu.calendar.support;

import bd.ac.mbstu.calendar.dao.DepartmentDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.dao.SqliteUserDao;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.EventFactory;
import bd.ac.mbstu.calendar.service.Session;
import bd.ac.mbstu.calendar.util.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDate;

/**
 * Base class for every test that needs a database.
 * Before each test: a brand new EMPTY in-memory SQLite database (the schema is created
 * automatically, so the departments ICT, CSE and EEE exist). After each test it is closed.
 */
public abstract class DatabaseTestBase {

    protected SqliteUserDao userDao;
    protected SqliteEventDao eventDao;
    protected DepartmentDao departmentDao;

    @BeforeEach
    protected void openFreshDatabase() {
        DatabaseManager.useUrl("jdbc:sqlite::memory:");
        DatabaseManager.getInstance();
        Session.getInstance().clear();
        userDao = new SqliteUserDao();
        eventDao = new SqliteEventDao();
        departmentDao = new DepartmentDao();
    }

    @AfterEach
    protected void closeDatabase() {
        DatabaseManager.getInstance().close();
        Session.getInstance().clear();
    }

    /** Id of a department by its code, e.g. deptId("ICT"). */
    protected long deptId(String code) {
        return departmentDao.findByCode(code).orElseThrow().getId();
    }

    /** Saves a user with a dummy password hash (fast). AuthServiceTest makes real BCrypt users. */
    protected User addUser(String username, Role role, String deptCode, Integer year, Integer semester) {
        Long dept = deptCode == null ? null : deptId(deptCode);
        return userDao.insert(new User(0, username, "not-a-real-hash", username + " name",
                role, dept, year, semester));
    }

    /** Builds (does NOT save) an official event with the type's defaults from EventFactory. */
    protected Event newEvent(EventType type, String title, LocalDate start, LocalDate end,
                             String deptCode, Integer year, Integer semester) {
        Event e = EventFactory.create(type, title, start, end);
        if (deptCode != null) {
            e.setDepartmentId((int) deptId(deptCode));
        }
        e.setYear(year);
        e.setSemester(semester);
        return e;
    }
}
