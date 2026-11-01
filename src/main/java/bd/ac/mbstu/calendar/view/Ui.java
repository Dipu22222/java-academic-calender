package bd.ac.mbstu.calendar.view;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.service.AuthenticationException;
import bd.ac.mbstu.calendar.service.PermissionDeniedException;
import bd.ac.mbstu.calendar.service.ai.AiException;
import bd.ac.mbstu.calendar.util.DataAccessException;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import bd.ac.mbstu.calendar.util.ValidationException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Small helpers shared by all screens: icons, buttons, dialogs, converters. */
public final class Ui {

    private static final Logger log = LoggerFactory.getLogger(Ui.class);

    /** Stand-in item for "no department filter / whole university" in combo boxes. */
    public static final Department ALL_DEPARTMENTS =
            new Department(0, "ALL", "All departments", "সব বিভাগ");

    private Ui() {
    }

    // ---------------------------------------------------------------
    // Icons and buttons
    // ---------------------------------------------------------------

    public static FontIcon icon(Ikon ikon, int size) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(size);
        return icon;
    }

    public static Button button(String text, Ikon ikon, String styleClass) {
        Button b = new Button(text);
        if (ikon != null) {
            b.setGraphic(icon(ikon, 14));
        }
        b.getStyleClass().add(styleClass);
        return b;
    }

    public static Button iconButton(Ikon ikon, String tooltip) {
        Button b = new Button();
        b.setGraphic(icon(ikon, 16));
        b.getStyleClass().add("icon-button");
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    // ---------------------------------------------------------------
    // Combo box helpers
    // ---------------------------------------------------------------

    public static <T> StringConverter<T> converter(Function<T, String> toText) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : toText.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        };
    }

    public static ObservableList<Department> departmentItems(AppContext ctx, boolean withAll) {
        ObservableList<Department> items = FXCollections.observableArrayList();
        if (withAll) {
            items.add(ALL_DEPARTMENTS);
        }
        items.addAll(ctx.getDepartments());
        return items;
    }

    public static StringConverter<Department> departmentConverter() {
        return converter(d -> d.getId() == 0 ? I18n.t("all_departments") : d.getCode());
    }

    /** 0 means "all years". */
    public static StringConverter<Integer> yearConverter() {
        return converter(y -> y == 0 ? I18n.t("all_years") : I18n.f("year_n", I18n.number(y)));
    }

    /** 0 means "all semesters". */
    public static StringConverter<Integer> semesterConverter() {
        return converter(s -> s == 0 ? I18n.t("all_semesters") : I18n.f("semester_n", I18n.number(s)));
    }

    /** The "ALL" stand-in becomes null (= no filter / whole university). */
    public static Integer deptIdOrNull(Department d) {
        return (d == null || d.getId() == 0) ? null : (int) d.getId();
    }

    public static Integer zeroToNull(Integer value) {
        return (value == null || value == 0) ? null : value;
    }

    // ---------------------------------------------------------------
    // Styling
    // ---------------------------------------------------------------

    /** Inline style that paints a node in the colour of an event type. */
    public static String chipStyle(EventType type) {
        return "-fx-background-color: " + type.getColorHex() + "; -fx-background-radius: 4;";
    }

    // ---------------------------------------------------------------
    // Errors and dialogs
    // ---------------------------------------------------------------

    /** Turns any exception into a message the user can understand. Unknown errors are logged. */
    public static String userMessage(Throwable t) {
        if (t instanceof ValidationException || t instanceof PermissionDeniedException
                || t instanceof AuthenticationException || t instanceof AiException) {
            return t.getMessage();
        }
        log.error("Unexpected error", t);
        return t instanceof DataAccessException ? I18n.t("err_database") : I18n.t("err_unexpected");
    }

    public static void error(String message) {
        alert(Alert.AlertType.ERROR, I18n.t("error_title"), message).showAndWait();
    }

    public static void info(String title, String message) {
        alert(Alert.AlertType.INFORMATION, title, message).showAndWait();
    }

    public static boolean confirm(String title, String message) {
        ButtonType yes = new ButtonType(I18n.t("yes"), ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType(I18n.t("no"), ButtonBar.ButtonData.NO);
        return alert(Alert.AlertType.CONFIRMATION, title, message, yes, no)
                .showAndWait().orElse(no) == yes;
    }

    private static Alert alert(Alert.AlertType type, String title, String message, ButtonType... buttons) {
        Alert alert = new Alert(type, message, buttons);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.getDialogPane().getStylesheets().setAll(Theme.stylesheets());
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE); // stops long texts being cut off
        return alert;
    }

    // ---------------------------------------------------------------
    // Events by day
    // ---------------------------------------------------------------

    /** Which events happen on which day (a multi-day event appears on every day it covers). */
    public static Map<LocalDate, List<Event>> groupByDay(List<Event> events, LocalDate from, LocalDate to) {
        Map<LocalDate, List<Event>> map = new HashMap<>();
        for (Event e : events) {
            LocalDate start = e.getStartDate().isBefore(from) ? from : e.getStartDate();
            LocalDate end = e.getEndDate().isAfter(to) ? to : e.getEndDate();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                map.computeIfAbsent(d, k -> new ArrayList<>()).add(e);
            }
        }
        return map;
    }
}
