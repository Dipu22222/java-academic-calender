package bd.ac.mbstu.calendar.service;

/** The logged-in user is not allowed to do this action. */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
