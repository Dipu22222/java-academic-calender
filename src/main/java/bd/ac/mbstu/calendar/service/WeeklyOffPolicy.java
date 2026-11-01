package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.SettingsDao;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Which weekdays are off every week (default: Friday and Saturday). */
public class WeeklyOffPolicy {

    private final Set<DayOfWeek> offDays;

    public WeeklyOffPolicy(Set<DayOfWeek> offDays) {
        this.offDays = new HashSet<>(offDays);
    }

    public static WeeklyOffPolicy defaultPolicy() {
        return new WeeklyOffPolicy(Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
    }

    /** Reads the "weekly_off_days" setting from the database. */
    public static WeeklyOffPolicy load() {
        try {
            return parse(new SettingsDao().get("weekly_off_days", "FRIDAY,SATURDAY"));
        } catch (RuntimeException e) {
            return defaultPolicy();
        }
    }

    /** "FRIDAY,SATURDAY" -> policy. Unknown names are ignored. */
    public static WeeklyOffPolicy parse(String csv) {
        Set<DayOfWeek> days = new HashSet<>();
        if (csv != null) {
            for (String part : csv.split(",")) {
                try {
                    days.add(DayOfWeek.valueOf(part.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // skip invalid day names
                }
            }
        }
        return days.isEmpty() ? defaultPolicy() : new WeeklyOffPolicy(days);
    }

    public boolean isOffDay(LocalDate date) {
        return offDays.contains(date.getDayOfWeek());
    }

    /** The weekly off days (used by the month view and when describing the policy to the AI). */
    public Set<DayOfWeek> getOffDays() {
        return Collections.unmodifiableSet(offDays);
    }
}
