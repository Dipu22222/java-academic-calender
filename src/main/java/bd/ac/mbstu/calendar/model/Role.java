package bd.ac.mbstu.calendar.model;

/** The three kinds of users. The permission rules live here in ONE place. */
public enum Role {

    ADMIN, TEACHER, STUDENT;

    /** Only the university office (ADMIN) may change official events. */
    public boolean canEditOfficialEvents() {
        return this == ADMIN;
    }

    /** Everybody may create personal events for themselves. */
    public boolean canCreatePersonalEvents() {
        return true;
    }

    public static Role fromString(String text) {
        if (text == null) {
            return STUDENT;
        }
        try {
            return Role.valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STUDENT;
        }
    }
}
