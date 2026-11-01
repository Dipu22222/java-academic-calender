package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.CalendarMonthDao;
import bd.ac.mbstu.calendar.dao.DepartmentDao;
import bd.ac.mbstu.calendar.dao.EventDao;
import bd.ac.mbstu.calendar.dao.SqliteEventDao;
import bd.ac.mbstu.calendar.dao.SqliteUserDao;
import bd.ac.mbstu.calendar.dao.UserDao;
import bd.ac.mbstu.calendar.model.CalendarMonth;
import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.PasswordUtil;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Loads the starting data when the app runs:
 *   1. demo users (admin / teacher / student)
 *   2. month names + holidays from  resources/seed/holidays_2026.json
 * Running it again does NOT create duplicates.
 */
public class SeedLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);
    private static final String SEED_FILE = "/seed/holidays_2026.json";

    private final EventDao eventDao = new SqliteEventDao();
    private final UserDao userDao = new SqliteUserDao();
    private final DepartmentDao departmentDao = new DepartmentDao();
    private final CalendarMonthDao monthDao = new CalendarMonthDao();

    public void seedAll() {
        seedUsers();
        seedHolidays(SEED_FILE);
    }

    // ---------------------------------------------------------------
    // Users
    // ---------------------------------------------------------------

    private void seedUsers() {
        if (userDao.count() > 0) {
            return;
        }
        Long ictId = departmentDao.findByCode("ICT").map(Department::getId).orElse(null);

        // CHANGE THESE PASSWORDS before showing the app to anyone else!
        userDao.insert(new User(0, "admin", PasswordUtil.hash("admin123"),
                "University Office (Admin)", Role.ADMIN, null, null, null));
        userDao.insert(new User(0, "teacher", PasswordUtil.hash("teacher123"),
                "Demo Teacher", Role.TEACHER, ictId, null, null));
        userDao.insert(new User(0, "student", PasswordUtil.hash("student123"),
                "Demo Student (ICT 2nd year)", Role.STUDENT, ictId, 2, 2));
        log.info("Created 3 demo users: admin, teacher, student");
    }

    // ---------------------------------------------------------------
    // Holidays
    // ---------------------------------------------------------------

    /** Loads a seed JSON file from the classpath. Returns how many holidays were added. */
    public int seedHolidays(String resourcePath) {
        SeedFile seed = readSeedFile(resourcePath);

        if (seed.months != null) {
            for (MonthInfo m : seed.months) {
                monthDao.save(new CalendarMonth(seed.year, m.month, m.bangla, m.hijri));
            }
        }

        int added = 0;
        if (seed.holidays != null) {
            for (HolidayInfo h : seed.holidays) {
                if (addHoliday(h)) {
                    added++;
                }
            }
        }
        log.info("Seed finished: {} new holidays added", added);
        return added;
    }

    private boolean addHoliday(HolidayInfo h) {
        // The event spans the class-off period (it is always the longer or equal one here).
        LocalDate start = LocalDate.parse(h.classOff.start);
        LocalDate end = LocalDate.parse(h.classOff.end);

        if (eventDao.existsByTitleAndStart(h.titleEn, start)) {
            return false; // already in the database
        }

        Event e = new Event();
        e.setTitle(h.titleEn);
        e.setTitleBn(h.titleBn);
        // "Summer Vacation" and "Winter Vacation" are breaks, everything else is a holiday
        e.setType(h.key != null && h.key.endsWith("vacation")
                ? EventType.SEMESTER_BREAK : EventType.HOLIDAY);
        e.setStartDate(start);
        e.setEndDate(end);
        e.setClassOff(true);
        e.setOfficeOff(h.officeOff != null);
        e.setDescription(buildDescription(h));
        eventDao.insert(e);
        return true;
    }

    /** Example: "Class off: 2026-03-12 to 2026-03-25 (14 days). Office off: ... (12 days)." */
    private String buildDescription(HolidayInfo h) {
        StringBuilder sb = new StringBuilder();
        sb.append("Class off: ").append(describe(h.classOff)).append(". ");
        if (h.officeOff != null) {
            sb.append("Office off: ").append(describe(h.officeOff)).append(".");
        } else {
            sb.append("Office remains open.");
        }
        return sb.toString();
    }

    private String describe(OffPeriod p) {
        String range = p.start.equals(p.end) ? p.start : p.start + " to " + p.end;
        return range + " (" + p.days + (p.days == 1 ? " day)" : " days)");
    }

    private SeedFile readSeedFile(String resourcePath) {
        try (InputStream in = SeedLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new DataAccessException("Seed file not found: " + resourcePath);
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, SeedFile.class);
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot read seed file " + resourcePath, e);
        }
    }

    // ---------------------------------------------------------------
    // Shapes that match the JSON file. Gson fills these automatically.
    // JSON fields we don't list here (note, verify, ...) are ignored.
    // ---------------------------------------------------------------

    private static class SeedFile {
        int year;
        List<MonthInfo> months;
        List<HolidayInfo> holidays;
    }

    private static class MonthInfo {
        int month;
        String bangla;
        String hijri;
    }

    private static class HolidayInfo {
        String key;
        String titleEn;
        String titleBn;
        OffPeriod classOff;
        OffPeriod officeOff;   // null when offices stay open
    }

    private static class OffPeriod {
        String start;
        String end;
        int days;
    }
}
