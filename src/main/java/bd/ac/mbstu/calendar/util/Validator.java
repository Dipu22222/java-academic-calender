package bd.ac.mbstu.calendar.util;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Small helper methods for checking input.
 * Every method throws ValidationException with a friendly message on failure.
 */
public final class Validator {

    private Validator() {
        // Utility class: nobody should create an object of it.
    }

    /** Text must not be null or only spaces. Returns the trimmed text. */
    public static String requireNotBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " must not be empty.");
        }
        return value.trim();
    }

    /** Object must not be null. */
    public static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new ValidationException(fieldName + " is required.");
        }
        return value;
    }

    /** End date must not be before start date. */
    public static void requireDateRange(LocalDate start, LocalDate end) {
        requireNotNull(start, "Start date");
        requireNotNull(end, "End date");
        if (end.isBefore(start)) {
            throw new ValidationException("End date cannot be before start date.");
        }
    }

    /** If both times are given, end time must be after start time. */
    public static void requireTimeRange(LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new ValidationException("End time must be after start time.");
        }
    }

    /** Number must be inside min..max (null is allowed and means "not set"). */
    public static void requireInRangeOrNull(Integer value, int min, int max, String fieldName) {
        if (value != null && (value < min || value > max)) {
            throw new ValidationException(fieldName + " must be between " + min + " and " + max + ".");
        }
    }
}
