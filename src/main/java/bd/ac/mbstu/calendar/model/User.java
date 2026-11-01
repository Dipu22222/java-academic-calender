package bd.ac.mbstu.calendar.model;

/** A person who can log in. */
public class User {

    private long id;
    private String username;
    private String passwordHash;   // BCrypt hash, never the real password
    private String fullName;
    private Role role;
    private Long departmentId;     // null for admin
    private Integer year;          // students only
    private Integer semester;      // students only

    public User(long id, String username, String passwordHash, String fullName,
                Role role, Long departmentId, Integer year, Integer semester) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.departmentId = departmentId;
        this.year = year;
        this.semester = semester;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Long getDepartmentId() { return departmentId; }
    public Integer getYear() { return year; }
    public Integer getSemester() { return semester; }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public String toString() {
        return fullName + " (" + role + ")";
    }
}
