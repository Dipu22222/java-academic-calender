package bd.ac.mbstu.calendar.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One calendar entry: a holiday, class test, exam, etc.
 * This is a plain model class (data + a few small helper methods).
 */
public class Event {

    private long id;                    // 0 means "not saved in the database yet"
    private String title;
    private String titleBn;
    private EventType type;
    private LocalDate startDate;
    private LocalDate endDate;          // same as startDate for one-day events
    private LocalTime startTime;        // optional
    private LocalTime endTime;          // optional
    private Integer departmentId;       // null = whole university
    private Integer year;               // null = all years
    private Integer semester;           // null = all semesters
    private String courseCode;
    private String description;
    private String location;
    private Integer reminderMinutes;    // 1440 = 1 day before, 60 = 1 hour, null = none
    private boolean classOff;
    private boolean officeOff;
    private Long ownerUserId;           // null = official event, otherwise a personal event
    private Long createdBy;

    public Event() {
        // empty constructor: create an Event and fill it with setters
    }

    // ---------- helper methods (the "date logic") ----------

    /** True if the event happens on the given day. */
    public boolean occursOn(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** True if this event and the other one share at least one day. */
    public boolean overlaps(Event other) {
        return !startDate.isAfter(other.endDate) && !other.startDate.isAfter(endDate);
    }

    public boolean isMultiDay() {
        return endDate.isAfter(startDate);
    }

    /** Official events belong to the university, personal ones to one user. */
    public boolean isPersonal() {
        return ownerUserId != null;
    }

    // ---------- getters and setters ----------

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleBn() { return titleBn; }
    public void setTitleBn(String titleBn) { this.titleBn = titleBn; }

    /** Title in the chosen language, falls back to English if no Bangla title exists. */
    public String getTitle(String language) {
        return ("bn".equals(language) && titleBn != null && !titleBn.isBlank()) ? titleBn : title;
    }

    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getDepartmentId() { return departmentId; }
    public void setDepartmentId(Integer departmentId) { this.departmentId = departmentId; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public String getCourseCode() { return courseCode; }
    public void setCourseCode(String courseCode) { this.courseCode = courseCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(Integer reminderMinutes) { this.reminderMinutes = reminderMinutes; }

    public boolean isClassOff() { return classOff; }
    public void setClassOff(boolean classOff) { this.classOff = classOff; }

    public boolean isOfficeOff() { return officeOff; }
    public void setOfficeOff(boolean officeOff) { this.officeOff = officeOff; }

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    @Override
    public String toString() {
        return type + ": " + title + " (" + startDate
                + (isMultiDay() ? " to " + endDate : "") + ")";
    }
}
