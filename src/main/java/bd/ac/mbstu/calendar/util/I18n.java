package bd.ac.mbstu.calendar.util;

import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.Role;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Translations for the whole UI ("en" or "bn").
 *   I18n.t("save")                 -> "Save" or "সংরক্ষণ"
 *   I18n.f("year_n", "2")          -> "Year 2" or "বর্ষ 2"
 * A missing Bangla text falls back to English, a missing key shows the key itself.
 */
public final class I18n {

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> BN = new HashMap<>();
    private static String language = "en";

    private static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};
    private static final String[] MONTHS_EN_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final String[] MONTHS_BN = {"জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"};

    // Index 0 = Sunday (we use  dayOfWeek.getValue() % 7 )
    private static final String[] DAYS_EN_SHORT = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final String[] DAYS_BN_SHORT = {"রবি", "সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র", "শনি"};
    private static final String[] DAYS_BN_FULL = {"রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার",
            "বৃহস্পতিবার", "শুক্রবার", "শনিবার"};

    private I18n() {
    }

    // ---------------------------------------------------------------
    // Language
    // ---------------------------------------------------------------

    public static String getLanguage() {
        return language;
    }

    public static boolean isBangla() {
        return "bn".equals(language);
    }

    public static void setLanguage(String lang) {
        language = "bn".equals(lang) ? "bn" : "en";
    }

    public static String t(String key) {
        String value = (isBangla() ? BN : EN).get(key);
        if (value == null) {
            value = EN.get(key);
        }
        return value != null ? value : key;
    }

    /** t(key) with String.format placeholders (%s). */
    public static String f(String key, Object... args) {
        return String.format(t(key), args);
    }

    // ---------------------------------------------------------------
    // Numbers, dates, names
    // ---------------------------------------------------------------

    public static String number(long n) {
        return isBangla() ? BanglaDate.toBanglaDigits(n) : String.valueOf(n);
    }

    public static String typeLabel(EventType type) {
        return type.getLabel(language);
    }

    public static String roleLabel(Role role) {
        return t("role_" + role.name().toLowerCase());
    }

    public static String dayShort(DayOfWeek day) {
        int i = day.getValue() % 7;
        return isBangla() ? DAYS_BN_SHORT[i] : DAYS_EN_SHORT[i];
    }

    /** "Wed, 28 Oct 2026" or "বুধবার, ২৮ অক্টোবর ২০২৬". */
    public static String dateLong(LocalDate d) {
        if (!isBangla()) {
            return DateDisplay.english(d);
        }
        return DAYS_BN_FULL[d.getDayOfWeek().getValue() % 7] + ", " + number(d.getDayOfMonth()) + " "
                + MONTHS_BN[d.getMonthValue() - 1] + " " + number(d.getYear());
    }

    /** "28 Oct" or "২৮ অক্টোবর". */
    public static String dayMonth(LocalDate d) {
        return isBangla()
                ? number(d.getDayOfMonth()) + " " + MONTHS_BN[d.getMonthValue() - 1]
                : d.getDayOfMonth() + " " + MONTHS_EN_SHORT[d.getMonthValue() - 1];
    }

    /** "October 2026" or "অক্টোবর ২০২৬". */
    public static String monthYear(YearMonth ym) {
        return isBangla()
                ? MONTHS_BN[ym.getMonthValue() - 1] + " " + number(ym.getYear())
                : MONTHS_EN[ym.getMonthValue() - 1] + " " + ym.getYear();
    }

    /** English date | Bangla date | Hijri date, in the current language. */
    public static String fullDate(LocalDate d) {
        String bangla = "";
        String hijri = "";
        try {
            bangla = BanglaDate.from(d).format(language);
            hijri = HijriDate.from(d).format(language);
        } catch (RuntimeException ignored) {
            // very unusual dates: just show what we have
        }
        return dateLong(d) + "  |  " + bangla + "  |  " + hijri;
    }

    /** 1440 -> "1 day", 60 -> "1 hour", 45 -> "45 minutes" (or Bangla). */
    public static String minutesText(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return isBangla() ? number(days) + " দিন" : days + (days == 1 ? " day" : " days");
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return isBangla() ? number(hours) + " ঘণ্টা" : hours + (hours == 1 ? " hour" : " hours");
        }
        return isBangla() ? number(minutes) + " মিনিট" : minutes + " minutes";
    }

    // ---------------------------------------------------------------
    // The texts
    // ---------------------------------------------------------------

    private static void add(String key, String en, String bn) {
        EN.put(key, en);
        BN.put(key, bn);
    }

    static {
        // login
        add("app_title", "Smart Academic Calendar", "স্মার্ট একাডেমিক ক্যালেন্ডার");
        add("university", "Mawlana Bhashani Science and Technology University",
                "মাওলানা ভাসানী বিজ্ঞান ও প্রযুক্তি বিশ্ববিদ্যালয়");
        add("login_subtitle", "Sign in to continue", "চালিয়ে যেতে সাইন ইন করুন");
        add("username", "Username", "ইউজারনেম");
        add("password", "Password", "পাসওয়ার্ড");
        add("login", "Sign in", "সাইন ইন");
        add("login_hint", "Demo accounts: admin / admin123, teacher / teacher123, student / student123",
                "ডেমো অ্যাকাউন্ট: admin / admin123, teacher / teacher123, student / student123");
        add("switch_language", "Switch language", "ভাষা পরিবর্তন করুন");
        add("switch_theme", "Switch light / dark theme", "লাইট / ডার্ক থিম পরিবর্তন করুন");

        // shell
        add("nav_calendar", "Calendar", "ক্যালেন্ডার");
        add("nav_ai", "AI Assistant", "এআই সহকারী");
        add("logout", "Sign out", "সাইন আউট");
        add("check_reminders", "Check reminders now", "এখনই রিমাইন্ডার দেখুন");
        add("no_reminders", "No reminders are due right now.", "এখন কোনো রিমাইন্ডার নেই।");
        add("info_title", "Information", "তথ্য");
        add("reminder_title", "Upcoming event", "আসন্ন ইভেন্ট");
        add("reminder_msg", "\"%s\" starts in %s (%s).", "\"%s\" শুরু হতে বাকি %s (%s)।");
        add("role_admin", "Admin", "অ্যাডমিন");
        add("role_teacher", "Teacher", "শিক্ষক");
        add("role_student", "Student", "শিক্ষার্থী");

        // common
        add("save", "Save", "সংরক্ষণ");
        add("delete", "Delete", "মুছুন");
        add("cancel", "Cancel", "বাতিল");
        add("close", "Close", "বন্ধ করুন");
        add("yes", "Yes", "হ্যাঁ");
        add("no", "No", "না");
        add("error_title", "Error", "ত্রুটি");
        add("err_database", "A database problem occurred. Please try again.",
                "ডাটাবেসে সমস্যা হয়েছে। আবার চেষ্টা করুন।");
        add("err_unexpected", "Something went wrong. Details were written to the log.",
                "কিছু একটা ভুল হয়েছে। বিস্তারিত লগে লেখা হয়েছে।");
        add("all_departments", "All departments", "সব বিভাগ");
        add("all_years", "All years", "সব বর্ষ");
        add("all_semesters", "All semesters", "সব সেমিস্টার");
        add("all_types", "All types", "সব ধরন");
        add("year_n", "Year %s", "বর্ষ %s");
        add("semester_n", "Semester %s", "সেমিস্টার %s");

        // calendar
        add("prev", "Previous", "আগে");
        add("next", "Next", "পরে");
        add("today", "Today", "আজ");
        add("view_month", "Month", "মাস");
        add("view_week", "Week", "সপ্তাহ");
        add("view_agenda", "Agenda", "তালিকা");
        add("add_event", "Add event", "ইভেন্ট যোগ করুন");
        add("go_to_date", "Go to date", "তারিখে যান");
        add("search_prompt", "Search events...", "ইভেন্ট খুঁজুন...");
        add("clear_filters", "Clear filters", "ফিল্টার মুছুন");
        add("bangla", "Bangla", "বাংলা");
        add("hijri", "Hijri", "হিজরি");
        add("search_results_title", "Search results (%s)", "অনুসন্ধানের ফলাফল (%s)");
        add("agenda_title", "Upcoming events", "আসন্ন ইভেন্ট");
        add("no_events", "No events found.", "কোনো ইভেন্ট পাওয়া যায়নি।");
        add("no_events_day", "No events on this day.", "এই দিনে কোনো ইভেন্ট নেই।");
        add("weekly_off_note", "Weekly off day", "সাপ্তাহিক ছুটির দিন");
        add("add_on_day", "Add event on this day", "এই দিনে ইভেন্ট যোগ করুন");
        add("more_n", "+%s more", "+%s আরও");
        add("badge_class_off", "Class off", "ক্লাস ছুটি");
        add("badge_office_off", "Office off", "অফিস ছুটি");
        add("badge_personal", "Personal", "ব্যক্তিগত");

        // event dialog
        add("dlg_add_event", "Add event", "ইভেন্ট যোগ করুন");
        add("dlg_edit_event", "Edit event", "ইভেন্ট সম্পাদনা");
        add("dlg_view_event", "Event details", "ইভেন্টের বিস্তারিত");
        add("f_title", "Title", "শিরোনাম");
        add("f_title_bn", "Title (Bangla)", "শিরোনাম (বাংলা)");
        add("f_type", "Type", "ধরন");
        add("f_start_date", "Start date", "শুরুর তারিখ");
        add("f_end_date", "End date", "শেষের তারিখ");
        add("f_time", "Time (HH:mm)", "সময় (HH:mm)");
        add("f_department", "Department", "বিভাগ");
        add("f_year", "Year", "বর্ষ");
        add("f_semester", "Semester", "সেমিস্টার");
        add("f_course", "Course code", "কোর্স কোড");
        add("f_location", "Location", "স্থান");
        add("f_reminder", "Reminder", "রিমাইন্ডার");
        add("f_description", "Description", "বিবরণ");
        add("f_class_off", "Classes off", "ক্লাস বন্ধ");
        add("f_office_off", "Office off", "অফিস বন্ধ");
        add("f_personal", "Personal event (only visible to me)", "ব্যক্তিগত ইভেন্ট (শুধু আমি দেখব)");
        add("personal_note", "This will be saved as your personal event. Only you can see it.",
                "এটি আপনার ব্যক্তিগত ইভেন্ট হিসেবে সংরক্ষিত হবে। শুধু আপনি দেখতে পাবেন।");
        add("quick_prompt", "Describe it, e.g. Add ICT-2207 class test on 15 Nov at 10am for 2nd year",
                "বর্ণনা লিখুন, যেমন: Add ICT-2207 class test on 15 Nov at 10am for 2nd year");
        add("btn_ai_fill", "AI fill", "এআই দিয়ে পূরণ");
        add("ai_working", "Working...", "কাজ চলছে...");
        add("quick_empty", "Type a short description first.", "আগে একটি ছোট বিবরণ লিখুন।");
        add("quick_done", "Form filled by %s. Please check it before saving.",
                "%s ফর্ম পূরণ করেছে। সংরক্ষণের আগে দেখে নিন।");
        add("time_invalid", "Time must look like 10:30 (24-hour clock).",
                "সময় হবে 10:30 এর মতো (২৪ ঘণ্টার ঘড়ি)।");
        add("reminder_none", "No reminder", "রিমাইন্ডার নেই");
        add("reminder_before", "%s before", "%s আগে");
        add("conflict_title", "Possible conflicts", "সম্ভাব্য সংঘর্ষ");
        add("conflict_intro", "The system found these problems:", "সিস্টেম এই সমস্যাগুলো পেয়েছে:");
        add("conflict_question", "Save anyway?", "তবুও সংরক্ষণ করবেন?");
        add("confirm_delete_title", "Delete event", "ইভেন্ট মুছুন");
        add("confirm_delete_msg", "Delete \"%s\"? This cannot be undone.",
                "\"%s\" মুছে ফেলবেন? এটি ফেরানো যাবে না।");

        // AI page
        add("ai_provider", "AI provider: %s", "এআই সরবরাহকারী: %s");
        add("tab_chat", "Chat", "চ্যাট");
        add("tab_digest", "Weekly digest", "সাপ্তাহিক ডাইজেস্ট");
        add("tab_study", "Study plan", "পড়ার পরিকল্পনা");
        add("tab_suggest", "Suggest dates", "তারিখ প্রস্তাব");
        add("tab_notice", "Notice to events", "নোটিশ থেকে ইভেন্ট");
        add("chat_welcome",
                "Hi! Ask me about holidays, class tests and exams. In offline mode I understand English questions like the examples below.",
                "হ্যালো! ছুটি, ক্লাস টেস্ট ও পরীক্ষা সম্পর্কে জিজ্ঞাসা করুন। অফলাইন মোডে নিচের উদাহরণের মতো ইংরেজি প্রশ্ন বুঝতে পারি।");
        add("chat_input", "Ask a question...", "প্রশ্ন লিখুন...");
        add("send", "Send", "পাঠান");
        add("ai_thinking", "Thinking...", "ভাবছি...");
        add("note", "Note", "বিঃদ্রঃ");
        add("generate", "Generate", "তৈরি করুন");
        add("week_of", "Any day in the week", "সপ্তাহের যেকোনো দিন");
        add("study_info", "Plan for your tests, exams and assignments in the next 120 days (%s).",
                "আগামী ১২০ দিনের আপনার টেস্ট, পরীক্ষা ও অ্যাসাইনমেন্টের পরিকল্পনা (%s)।");
        add("study_btn", "Generate my study plan", "আমার পড়ার পরিকল্পনা তৈরি করুন");
        add("suggest_from", "From", "থেকে");
        add("suggest_to", "To", "পর্যন্ত");
        add("suggest_btn", "Find best dates", "সেরা তারিখ খুঁজুন");
        add("notice_prompt", "Paste a notice here...", "এখানে নোটিশ পেস্ট করুন...");
        add("btn_extract", "Extract events", "ইভেন্ট বের করুন");
        add("btn_add_selected", "Add selected to calendar", "নির্বাচিতগুলো ক্যালেন্ডারে যোগ করুন");
        add("btn_edit", "Edit...", "সম্পাদনা...");
        add("notice_found", "%s proposed events found. Check them, then add. Nothing is saved yet.",
                "%s টি প্রস্তাবিত ইভেন্ট পাওয়া গেছে। দেখে নিয়ে যোগ করুন। এখনো কিছু সংরক্ষিত হয়নি।");
        add("notice_none", "No dated events were found in the notice.",
                "নোটিশে তারিখসহ কোনো ইভেন্ট পাওয়া যায়নি।");
        add("added", "Added", "যোগ হয়েছে");
        add("added_summary", "%s added, %s failed.", "%s টি যোগ হয়েছে, %s টি ব্যর্থ।");

        // import / export
        add("btn_import", "Import", "ইমপোর্ট");
        add("btn_export", "Export", "এক্সপোর্ট");
        add("import_title", "Import calendar", "ক্যালেন্ডার ইমপোর্ট");
        add("export_title", "Export calendar", "ক্যালেন্ডার এক্সপোর্ট");
        add("choose_file", "Choose file...", "ফাইল বাছাই করুন...");
        add("save_template", "Save CSV template...", "CSV টেমপ্লেট সংরক্ষণ...");
        add("import_btn", "Import events", "ইভেন্ট ইমপোর্ট করুন");
        add("import_hint", "Supported files: .csv, .json, .xlsx. Nothing is saved until you press Import events.",
                "সমর্থিত ফাইল: .csv, .json, .xlsx। \"ইভেন্ট ইমপোর্ট করুন\" চাপার আগে কিছু সংরক্ষিত হয় না।");
        add("import_summary", "%s events ready to import, %s rows with problems.",
                "%s টি ইভেন্ট ইমপোর্টের জন্য প্রস্তুত, %s টি সারিতে সমস্যা আছে।");
        add("import_problems", "Problems found (these rows are skipped):",
                "পাওয়া সমস্যা (এই সারিগুলো বাদ যাবে):");
        add("import_done", "%s added, %s skipped (already in the calendar), %s failed.",
                "%s টি যোগ হয়েছে, %s টি বাদ (আগে থেকেই আছে), %s টি ব্যর্থ।");
        add("col_row", "Row", "সারি");
        add("export_format", "Format", "ফরম্যাট");
        add("export_btn", "Export", "এক্সপোর্ট করুন");
        add("export_note", "Only official events are exported (not personal events). PDF files use English text only.",
                "শুধু অফিসিয়াল ইভেন্ট এক্সপোর্ট হয় (ব্যক্তিগত ইভেন্ট নয়)। PDF ফাইলে শুধু ইংরেজি লেখা থাকে।");
        add("export_done", "%s events exported to:\n%s", "%s টি ইভেন্ট এক্সপোর্ট হয়েছে:\n%s");
    }
}
