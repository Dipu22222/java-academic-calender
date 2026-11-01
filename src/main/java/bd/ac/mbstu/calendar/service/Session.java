package bd.ac.mbstu.calendar.service;

import bd.ac.mbstu.calendar.model.User;

/** Remembers who is logged in while the app is open. */
public final class Session {

    private static final Session INSTANCE = new Session();

    private volatile User currentUser;

    private Session() {
    }

    public static Session getInstance() {
        return INSTANCE;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public User requireUser() {
        if (currentUser == null) {
            throw new AuthenticationException("Please log in first.");
        }
        return currentUser;
    }

    public void clear() {
        currentUser = null;
    }
}
