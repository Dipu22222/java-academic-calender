package bd.ac.mbstu.calendar.model;

import java.time.LocalDate;

/**
 * Describes WHICH events we want (a search form as an object).
 * Every field is optional. Methods return "this", so you can chain them:
 *     new EventFilter().from(a).to(b).type(EventType.EXAM)
 */
public class EventFilter {

    private LocalDate from;          // events that end on/after this day
    private LocalDate to;            // events that start on/before this day
    private EventType type;
    private Integer departmentId;    // also matches university-wide events (department = null)
    private Integer year;            // also matches events for all years
    private Integer semester;        // also matches events for all semesters
    private String keyword;
    private Long viewerUserId;       // whose personal events to include (official always included)

    public EventFilter from(LocalDate from) { this.from = from; return this; }
    public EventFilter to(LocalDate to) { this.to = to; return this; }
    public EventFilter type(EventType type) { this.type = type; return this; }
    public EventFilter department(Integer departmentId) { this.departmentId = departmentId; return this; }
    public EventFilter year(Integer year) { this.year = year; return this; }
    public EventFilter semester(Integer semester) { this.semester = semester; return this; }
    public EventFilter keyword(String keyword) { this.keyword = keyword; return this; }
    public EventFilter viewer(Long viewerUserId) { this.viewerUserId = viewerUserId; return this; }

    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public EventType getType() { return type; }
    public Integer getDepartmentId() { return departmentId; }
    public Integer getYear() { return year; }
    public Integer getSemester() { return semester; }
    public String getKeyword() { return keyword; }
    public Long getViewerUserId() { return viewerUserId; }
}
