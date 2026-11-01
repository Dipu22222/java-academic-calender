package bd.ac.mbstu.calendar.support;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.EventFactory;
import bd.ac.mbstu.calendar.service.WeeklyOffPolicy;
import bd.ac.mbstu.calendar.service.ai.AiContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Fixed sample data for tests that need NO database (the offline assistant, prompts, exporter).
 * "Today" is Wednesday 28 October 2026. Department ids: ICT = 1, CSE = 2, EEE = 3.
 */
public final class TestData {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private TestData() {
    }

    public static List<Department> departments() {
        return List.of(
                new Department(1, "ICT", "Information and Communication Technology", "তথ্য ও যোগাযোগ প্রযুক্তি"),
                new Department(2, "CSE", "Computer Science and Engineering", "কম্পিউটার বিজ্ঞান ও প্রকৌশল"),
                new Department(3, "EEE", "Electrical and Electronic Engineering", "ইলেকট্রিক্যাল ও ইলেকট্রনিক ইঞ্জিনিয়ারিং"));
    }

    /** An ICT, year 2, semester 2 student. */
    public static User student() {
        return new User(1, "rahim", "hash", "Rahim Uddin", Role.STUDENT, 1L, 2, 2);
    }

    public static User admin() {
        return new User(2, "admin", "hash", "University Office", Role.ADMIN, null, null, null);
    }

    /** Builds an official event (not saved anywhere) with the type's defaults from EventFactory. */
    public static Event make(long id, EventType type, String title, LocalDate start, LocalDate end,
                             Integer dept, Integer year, Integer semester) {
        Event e = EventFactory.create(type, title, start, end);
        e.setId(id);
        e.setDepartmentId(dept);
        e.setYear(year);
        e.setSemester(semester);
        return e;
    }

    private static LocalDate d(int month, int day) {
        return LocalDate.of(2026, month, day);
    }

    /** Eight events: holidays, a vacation, two class tests, an exam, an assignment and a past holiday. */
    public static List<Event> events() {
        Event bhashani = make(1, EventType.HOLIDAY, "Bhashani Anniversary", d(11, 17), d(11, 17), null, null, null);
        bhashani.setOfficeOff(false);                                   // classes off, office open
        Event victory = make(2, EventType.HOLIDAY, "Victory Day", d(12, 16), d(12, 16), null, null, null);
        Event winter = make(3, EventType.SEMESTER_BREAK, "Winter Vacation", d(12, 26), d(12, 30), null, null, null);
        Event ictTest = make(4, EventType.CLASS_TEST, "ICT-2207 Class Test", d(11, 15), d(11, 15), 1, 2, 2);
        ictTest.setStartTime(LocalTime.of(10, 0));
        Event cseTest = make(5, EventType.CLASS_TEST, "CSE-3101 Class Test", d(11, 16), d(11, 16), 2, 3, 1);
        Event exam = make(6, EventType.EXAM, "ICT-2201 Final Exam", d(12, 6), d(12, 6), 1, 2, 2);
        Event assignment = make(7, EventType.ASSIGNMENT_DEADLINE, "ICT-2209 Assignment", d(11, 10), d(11, 10), 1, 2, 2);
        Event past = make(8, EventType.HOLIDAY, "Durga Puja", d(10, 18), d(10, 21), null, null, null);
        return List.of(bhashani, victory, winter, ictTest, cseTest, exam, assignment, past);
    }

    public static AiContext context(User user, List<Event> events) {
        return new AiContext(TODAY, user, events, departments(), WeeklyOffPolicy.defaultPolicy());
    }
}
