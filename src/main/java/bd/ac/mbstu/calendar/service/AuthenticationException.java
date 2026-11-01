package bd.ac.mbstu.calendar.service;

/** Wrong username/password, too many attempts, or not logged in. */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }
}
