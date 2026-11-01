package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.dao.SqliteUserDao;
import bd.ac.mbstu.calendar.dao.UserDao;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.util.PasswordUtil;
import bd.ac.mbstu.calendar.util.ValidationException;
import bd.ac.mbstu.calendar.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Login, logout and (admin only) creating new users. */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_TIME = Duration.ofSeconds(30);

    private final UserDao userDao;
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    public AuthService() {
        this(new SqliteUserDao());
    }

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    /** Checks the password and, if correct, remembers the user in the Session. */
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AuthenticationException("Please enter your username and password.");
        }
        String key = username.trim().toLowerCase();

        Instant until = lockedUntil.get(key);
        if (until != null && Instant.now().isBefore(until)) {
            long seconds = Duration.between(Instant.now(), until).getSeconds() + 1;
            throw new AuthenticationException("Too many failed attempts. Try again in " + seconds + " seconds.");
        }

        Optional<User> found = userDao.findByUsername(key);
        boolean ok = found.isPresent() && PasswordUtil.verify(password, found.get().getPasswordHash());

        if (!ok) {
            int attempts = failedAttempts.merge(key, 1, Integer::sum);
            log.warn("Failed login for '{}' (attempt {})", key, attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntil.put(key, Instant.now().plus(LOCK_TIME));
                failedAttempts.remove(key);
            }
            // Same message for "no such user" and "wrong password" so attackers learn nothing.
            throw new AuthenticationException("Invalid username or password.");
        }

        failedAttempts.remove(key);
        lockedUntil.remove(key);
        Session.getInstance().setCurrentUser(found.get());
        log.info("User '{}' logged in as {}", key, found.get().getRole());
        return found.get();
    }

    public void logout() {
        User u = Session.getInstance().getCurrentUser();
        if (u != null) {
            log.info("User '{}' logged out", u.getUsername());
        }
        Session.getInstance().clear();
    }

    /** Only an ADMIN may create accounts. */
    public User createUser(User actor, String username, String password, String fullName,
                           Role role, Long departmentId, Integer year, Integer semester) {
        if (actor == null || !actor.isAdmin()) {
            throw new PermissionDeniedException("Only an admin can create users.");
        }
        String u = Validator.requireNotBlank(username, "Username").toLowerCase();
        if (u.length() < 3 || !u.matches("[a-z0-9._-]+")) {
            throw new ValidationException("Username must be 3+ characters: letters, digits, . _ - only.");
        }
        if (password == null || password.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters.");
        }
        String name = Validator.requireNotBlank(fullName, "Full name");
        Validator.requireNotNull(role, "Role");
        Validator.requireInRangeOrNull(year, 1, 4, "Year");
        Validator.requireInRangeOrNull(semester, 1, 2, "Semester");
        if (userDao.findByUsername(u).isPresent()) {
            throw new ValidationException("This username is already taken.");
        }
        return userDao.insert(new User(0, u, PasswordUtil.hash(password), name,
                role, departmentId, year, semester));
    }
}
