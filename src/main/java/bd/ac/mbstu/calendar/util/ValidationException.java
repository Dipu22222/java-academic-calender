package bd.ac.mbstu.calendar.util;

/**
 * Thrown when user input is invalid (empty title, end date before start date...).
 * The UI catches this and shows the message to the user.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
