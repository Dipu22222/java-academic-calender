package bd.ac.mbstu.calendar.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing with BCrypt.
 * We NEVER store the real password, only the hash. BCrypt adds a random
 * "salt", so two users with the same password get different hashes.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /** Turns "admin123" into something like "$2a$10$...". */
    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    /** Checks a typed password against the stored hash. */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            return false; // stored hash was not a valid BCrypt hash
        }
    }
}
