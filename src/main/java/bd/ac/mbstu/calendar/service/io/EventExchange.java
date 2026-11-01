package bd.ac.mbstu.calendar.service.io;

import java.util.List;

/**
 * One event as it appears in an import/export file (CSV, JSON, Excel).
 * Public fields with simple types, so Gson can read and write it directly.
 */
public class EventExchange {

    public String title;
    public String titleBn;
    public String type;            // CLASS_TEST, EXAM, HOLIDAY...
    public String startDate;       // yyyy-MM-dd
    public String endDate;
    public String startTime;       // HH:mm
    public String endTime;
    public String department;      // code, e.g. ICT (null = whole university)
    public Integer year;
    public Integer semester;
    public String courseCode;
    public String location;
    public String description;
    public Integer reminderMinutes; // 0 = no reminder, null = use the default for the type
    public Boolean classOff;
    public Boolean officeOff;

    /** The wrapper written to .json files. */
    public static class ExchangeFile {
        public String format;
        public int version;
        public String exportedAt;
        public List<EventExchange> events;
    }
}
