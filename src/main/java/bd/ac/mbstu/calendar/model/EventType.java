package bd.ac.mbstu.calendar.model;

/**
 * The 7 kinds of calendar entries. Each one knows its English name,
 * Bangla name and display colour, so the UI never needs a big if/else.
 */
public enum EventType {

    HOLIDAY("Holiday", "ছুটি", "#D32F2F"),
    CLASS_TEST("Class Test", "ক্লাস টেস্ট", "#F57C00"),
    EXAM("Exam", "পরীক্ষা", "#7B1FA2"),
    SEMESTER_BREAK("Semester Break", "সেমিস্টার বিরতি", "#388E3C"),
    ASSIGNMENT_DEADLINE("Assignment Deadline", "অ্যাসাইনমেন্ট জমার সময়", "#1976D2"),
    DEPARTMENT_EVENT("Department Event", "বিভাগীয় অনুষ্ঠান", "#00897B"),
    NOTICE("Seminar / Notice", "সেমিনার / নোটিশ", "#607D8B");

    private final String labelEn;
    private final String labelBn;
    private final String colorHex;

    EventType(String labelEn, String labelBn, String colorHex) {
        this.labelEn = labelEn;
        this.labelBn = labelBn;
        this.colorHex = colorHex;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public String getLabelBn() {
        return labelBn;
    }

    public String getColorHex() {
        return colorHex;
    }

    /** Label in the chosen language ("en" or "bn"). */
    public String getLabel(String language) {
        return "bn".equals(language) ? labelBn : labelEn;
    }

    /** Safe conversion from text (used by import and AI). Returns null if unknown. */
    public static EventType fromString(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        for (EventType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
