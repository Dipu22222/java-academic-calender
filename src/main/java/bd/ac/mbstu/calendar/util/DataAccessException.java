package bd.ac.mbstu.calendar.util;

/**
 * Unchecked exception thrown when a database operation fails.
 * Wrapping SQLException in our own exception means the rest of the
 * app does not need "throws SQLException" everywhere.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
