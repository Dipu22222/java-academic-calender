package bd.ac.mbstu.calendar.model;

/** A university department such as ICT or CSE. */
public class Department {

    private long id;
    private String code;
    private String nameEn;
    private String nameBn;

    public Department(long id, String code, String nameEn, String nameBn) {
        this.id = id;
        this.code = code;
        this.nameEn = nameEn;
        this.nameBn = nameBn;
    }

    public long getId() { return id; }
    public String getCode() { return code; }
    public String getNameEn() { return nameEn; }
    public String getNameBn() { return nameBn; }

    /** Name in the chosen language ("en" or "bn"). */
    public String getName(String language) {
        return ("bn".equals(language) && nameBn != null) ? nameBn : nameEn;
    }

    /** Shown in dropdown boxes. */
    @Override
    public String toString() {
        return code;
    }
}
