package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.service.ReminderService;
import bd.ac.mbstu.calendar.util.Async;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import bd.ac.mbstu.calendar.view.NotificationPopup;
import bd.ac.mbstu.calendar.view.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.time.LocalDate;

/**
 * The main window after login: sidebar on the left, top bar, and one page in the middle
 * (Calendar or AI Assistant). Both pages are created once and swapped, so the calendar
 * keeps its filters and selected day when you visit the AI page and come back.
 */
public class MainController {

    public static final int PAGE_CALENDAR = 0;
    public static final int PAGE_AI = 1;

    private final AppContext ctx;
    private final AppShell shell;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Label pageTitle = new Label();

    private final CalendarController calendar;
    private final AiController ai;
    private final ReminderService reminders = new ReminderService();

    private final ToggleButton calendarButton = new ToggleButton();
    private final ToggleButton aiButton = new ToggleButton();
    private Button bellButton;
    private boolean switching;      // stops the nav listener from calling itself
    private int page;

    public MainController(AppContext ctx, AppShell shell, int startPage) {
        this.ctx = ctx;
        this.shell = shell;
        this.calendar = new CalendarController(ctx);
        this.ai = new AiController(ctx);

        root.setLeft(buildSidebar());
        VBox right = new VBox(buildTopBar(), content);
        VBox.setVgrow(content, Priority.ALWAYS);
        root.setCenter(right);

        showPage(startPage);
        startReminders();
    }

    public Parent getView() {
        return root;
    }

    public int getPage() {
        return page;
    }

    /** Stops background work and listeners. Called on logout and before the screen is rebuilt. */
    public void dispose() {
        reminders.stop();
        calendar.dispose();
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    private Node buildSidebar() {
        Label brand = new Label("MBSTU");
        brand.getStyleClass().add("brand-title");
        Label brandSub = new Label(I18n.t("app_title"));
        brandSub.getStyleClass().add("brand-sub");
        brandSub.setWrapText(true);
        HBox brandRow = new HBox(10, Ui.icon(FontAwesomeSolid.CALENDAR_CHECK, 24), brand);
        brandRow.setAlignment(Pos.CENTER_LEFT);
        VBox brandBox = new VBox(2, brandRow, brandSub);
        brandBox.setPadding(new Insets(0, 0, 18, 6));

        ToggleGroup group = new ToggleGroup();
        setupNav(calendarButton, I18n.t("nav_calendar"), FontAwesomeSolid.CALENDAR_ALT, group, PAGE_CALENDAR);
        setupNav(aiButton, I18n.t("nav_ai"), FontAwesomeSolid.ROBOT, group, PAGE_AI);
        group.selectedToggleProperty().addListener((o, oldToggle, newToggle) -> {
            if (switching) {
                return;
            }
            if (newToggle == null) {
                oldToggle.setSelected(true); // one page must always be selected
                return;
            }
            showPage((Integer) newToggle.getUserData());
        });

        Label name = new Label(ctx.getUser().getFullName());
        name.getStyleClass().add("user-name");
        name.setWrapText(true);
        Label role = new Label(I18n.roleLabel(ctx.getUser().getRole()));
        role.getStyleClass().add("user-role");

        bellButton = sidebarButton(I18n.t("check_reminders"), FontAwesomeSolid.BELL);
        bellButton.setOnAction(e -> checkRemindersNow());

        Button language = sidebarButton(I18n.isBangla() ? "English" : "বাংলা", FontAwesomeSolid.LANGUAGE);
        language.setTooltip(new Tooltip(I18n.t("switch_language")));
        language.setOnAction(e -> shell.toggleLanguage());

        Button theme = sidebarButton(Theme.isDark() ? "Light" : "Dark",
                Theme.isDark() ? FontAwesomeSolid.SUN : FontAwesomeSolid.MOON);
        theme.setTooltip(new Tooltip(I18n.t("switch_theme")));
        theme.setOnAction(e -> shell.toggleTheme());

        Button logout = sidebarButton(I18n.t("logout"), FontAwesomeSolid.SIGN_OUT_ALT);
        logout.setOnAction(e -> shell.logout());

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox sidebar = new VBox(6, brandBox, calendarButton, aiButton, spacer,
                new VBox(2, name, role), bellButton, language, theme, logout);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(230);
        sidebar.setMinWidth(230);
        return sidebar;
    }

    private Node buildTopBar() {
        pageTitle.getStyleClass().add("page-title");
        Label date = new Label(I18n.fullDate(LocalDate.now()));
        date.getStyleClass().add("muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, pageTitle, spacer, date);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private void setupNav(ToggleButton button, String text, Ikon ikon,
                          ToggleGroup group, int pageNumber) {
        button.setText(text);
        button.setGraphic(Ui.icon(ikon, 16));
        button.setGraphicTextGap(10);
        button.setToggleGroup(group);
        button.setUserData(pageNumber);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
    }

    private Button sidebarButton(String text, Ikon ikon) {
        Button b = Ui.button(text, ikon, "light-button");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }

    // ---------------------------------------------------------------
    // Pages
    // ---------------------------------------------------------------

    private void showPage(int pageNumber) {
        page = pageNumber == PAGE_AI ? PAGE_AI : PAGE_CALENDAR;
        switching = true;
        try {
            calendarButton.setSelected(page == PAGE_CALENDAR);
            aiButton.setSelected(page == PAGE_AI);
        } finally {
            switching = false;
        }
        if (page == PAGE_AI) {
            content.getChildren().setAll(ai.getView());
            pageTitle.setText(I18n.t("nav_ai"));
        } else {
            content.getChildren().setAll(calendar.getView());
            pageTitle.setText(I18n.t("nav_calendar"));
        }
    }

    // ---------------------------------------------------------------
    // Reminders
    // ---------------------------------------------------------------

    /**
     * Students and teachers get automatic pop-ups for events of THEIR group.
     * The admin sees events of every department, so an automatic stream of pop-ups
     * would be noise: the admin uses the bell button instead.
     */
    private void startReminders() {
        if (!ctx.isAdmin()) {
            reminders.start(ctx.getUser(), this::showReminder);
        }
    }

    private void showReminder(ReminderService.Reminder reminder) {
        String when = reminder.eventStart().toLocalDate() + " " + reminder.eventStart().toLocalTime();
        NotificationPopup.show(I18n.t("reminder_title"),
                I18n.f("reminder_msg", reminder.event().getTitle(I18n.getLanguage()),
                        I18n.minutesText(reminder.minutesBefore()), when));
    }

    private void checkRemindersNow() {
        bellButton.setDisable(true);
        Async.run(() -> reminders.checkNow(ctx.getUser()),
                list -> {
                    bellButton.setDisable(false);
                    if (list.isEmpty()) {
                        Ui.info(I18n.t("info_title"), I18n.t("no_reminders"));
                    } else {
                        list.forEach(this::showReminder);
                    }
                },
                err -> {
                    bellButton.setDisable(false);
                    Ui.error(Ui.userMessage(err));
                });
    }
}
