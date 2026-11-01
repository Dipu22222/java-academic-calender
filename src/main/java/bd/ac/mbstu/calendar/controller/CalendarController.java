package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.dao.CalendarMonthDao;
import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventFilter;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.service.EventBus;
import bd.ac.mbstu.calendar.util.DateUtil;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.view.AgendaListView;
import bd.ac.mbstu.calendar.view.EventCard;
import bd.ac.mbstu.calendar.view.MonthGridView;
import bd.ac.mbstu.calendar.view.Ui;
import bd.ac.mbstu.calendar.view.WeekGridView;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * The calendar page: toolbar, filters, month/week/agenda view, "selected day" panel, legend.
 * It subscribes to the EventBus, so it refreshes by itself when events change anywhere.
 */
public class CalendarController {

    private enum ViewMode { MONTH, WEEK, AGENDA }

    /** One entry of the type filter (type == null means "all types"). */
    private record TypeOption(EventType type) {
    }

    private static final TypeOption ALL_TYPES = new TypeOption(null);

    private final AppContext ctx;
    private final BorderPane root = new BorderPane();

    // Observer: any data change triggers a refresh on the UI thread.
    private final EventBus.DataChangeListener listener = type -> Platform.runLater(this::refresh);

    private final ComboBox<Department> deptBox = new ComboBox<>();
    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final ComboBox<Integer> semesterBox = new ComboBox<>();
    private final ComboBox<TypeOption> typeBox = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final DatePicker jumpPicker = new DatePicker();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final StackPane centerHolder = new StackPane();
    private final VBox detailsPane = new VBox(10);
    private final Label detailsTitle = new Label();
    private final VBox detailsList = new VBox(8);

    private ViewMode mode = ViewMode.MONTH;
    private LocalDate anchor = LocalDate.now();     // the date the view is built around
    private LocalDate selected = LocalDate.now();   // the highlighted day
    private List<Event> loaded = List.of();
    private MonthGridView monthView;
    private WeekGridView weekView;

    public CalendarController(AppContext ctx) {
        this.ctx = ctx;
        root.getStyleClass().add("page");
        root.setPadding(new Insets(16));

        configureFilters();
        root.setTop(new VBox(10, buildToolbar(), buildFilterBar()));
        BorderPane.setMargin(root.getTop(), new Insets(0, 0, 12, 0));

        centerHolder.getStyleClass().add("card");
        centerHolder.setPadding(new Insets(8));
        root.setCenter(centerHolder);

        buildDetailsPane();
        root.setRight(detailsPane);
        BorderPane.setMargin(detailsPane, new Insets(0, 0, 0, 12));

        root.setBottom(buildLegend());
        BorderPane.setMargin(root.getBottom(), new Insets(10, 0, 0, 0));

        EventBus.getInstance().subscribe(listener);
        refresh();
    }

    public Node getView() {
        return root;
    }

    /** Called when the page is thrown away (language/theme change, logout). */
    public void dispose() {
        EventBus.getInstance().unsubscribe(listener);
    }

    // ---------------------------------------------------------------
    // Building the controls
    // ---------------------------------------------------------------

    private Node buildToolbar() {
        Button prev = Ui.iconButton(FontAwesomeSolid.CHEVRON_LEFT, I18n.t("prev"));
        Button next = Ui.iconButton(FontAwesomeSolid.CHEVRON_RIGHT, I18n.t("next"));
        Button todayBtn = Ui.button(I18n.t("today"), null, "ghost-button");
        prev.setOnAction(e -> move(-1));
        next.setOnAction(e -> move(1));
        todayBtn.setOnAction(e -> {
            anchor = LocalDate.now();
            selected = anchor;
            refresh();
        });

        titleLabel.getStyleClass().add("calendar-title");
        subtitleLabel.getStyleClass().add("muted");

        ToggleGroup group = new ToggleGroup();
        ToggleButton monthBtn = segment(I18n.t("view_month"), group, ViewMode.MONTH);
        ToggleButton weekBtn = segment(I18n.t("view_week"), group, ViewMode.WEEK);
        ToggleButton agendaBtn = segment(I18n.t("view_agenda"), group, ViewMode.AGENDA);
        monthBtn.setSelected(true);
        group.selectedToggleProperty().addListener((o, oldToggle, newToggle) -> {
            if (newToggle == null) {
                oldToggle.setSelected(true); // one view must always be selected
                return;
            }
            mode = (ViewMode) newToggle.getUserData();
            refresh();
        });

        jumpPicker.setPromptText(I18n.t("go_to_date"));
        jumpPicker.setPrefWidth(150);
        jumpPicker.valueProperty().addListener((o, oldDate, newDate) -> {
            if (newDate != null) {
                anchor = newDate;
                selected = newDate;
                refresh();
            }
        });

        Button add = Ui.button(I18n.t("add_event"), FontAwesomeSolid.PLUS, "primary-button");
        add.setOnAction(e -> openNew(selected));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(14,
                new HBox(4, prev, todayBtn, next),
                new VBox(2, titleLabel, subtitleLabel),
                spacer, jumpPicker, new HBox(0, monthBtn, weekBtn, agendaBtn), add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private ToggleButton segment(String text, ToggleGroup group, ViewMode viewMode) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setUserData(viewMode);
        b.getStyleClass().add("segment-button");
        return b;
    }

    private Node buildFilterBar() {
        searchField.setPromptText(I18n.t("search_prompt"));
        searchField.setPrefWidth(220);
        Button clear = Ui.button(I18n.t("clear_filters"), FontAwesomeSolid.TIMES, "ghost-button");
        clear.setOnAction(e -> clearFilters());

        FlowPane bar = new FlowPane(8, 6, searchField, typeBox, deptBox, yearBox, semesterBox, clear);
        bar.setAlignment(Pos.CENTER_LEFT);

        if (ctx.isAdmin()) { // only the university office sees these buttons
            Button importBtn = Ui.button(I18n.t("btn_import"), FontAwesomeSolid.FILE_IMPORT, "ghost-button");
            Button exportBtn = Ui.button(I18n.t("btn_export"), FontAwesomeSolid.FILE_EXPORT, "ghost-button");
            importBtn.setOnAction(e -> ImportExportController.showImport(ctx, root.getScene().getWindow()));
            exportBtn.setOnAction(e -> ImportExportController.showExport(ctx, root.getScene().getWindow()));
            bar.getChildren().addAll(importBtn, exportBtn);
        }
        return bar;
    }

    /** Fills the filter boxes, sets the defaults, and ONLY THEN starts listening for changes. */
    private void configureFilters() {
        deptBox.setItems(Ui.departmentItems(ctx, true));
        deptBox.setConverter(Ui.departmentConverter());
        yearBox.setItems(FXCollections.observableArrayList(0, 1, 2, 3, 4));
        yearBox.setConverter(Ui.yearConverter());
        semesterBox.setItems(FXCollections.observableArrayList(0, 1, 2));
        semesterBox.setConverter(Ui.semesterConverter());

        List<TypeOption> options = new ArrayList<>();
        options.add(ALL_TYPES);
        for (EventType t : EventType.values()) {
            options.add(new TypeOption(t));
        }
        typeBox.setItems(FXCollections.observableArrayList(options));
        typeBox.setConverter(Ui.converter(o -> o.type() == null
                ? I18n.t("all_types") : I18n.typeLabel(o.type())));

        applyDefaultFilters();

        PauseTransition debounce = new PauseTransition(Duration.millis(350));
        debounce.setOnFinished(e -> refresh());
        searchField.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        deptBox.valueProperty().addListener((o, a, b) -> refresh());
        yearBox.valueProperty().addListener((o, a, b) -> refresh());
        semesterBox.valueProperty().addListener((o, a, b) -> refresh());
        typeBox.valueProperty().addListener((o, a, b) -> refresh());
    }

    /** Students and teachers start on their own group; admin starts on "all". */
    private void applyDefaultFilters() {
        User u = ctx.getUser();
        deptBox.setValue(ctx.findDepartment(u.getDepartmentId()).orElse(Ui.ALL_DEPARTMENTS));
        yearBox.setValue(u.getYear() == null ? 0 : u.getYear());
        semesterBox.setValue(u.getSemester() == null ? 0 : u.getSemester());
        typeBox.setValue(ALL_TYPES);
        searchField.clear();
    }

    private void clearFilters() {
        deptBox.setValue(Ui.ALL_DEPARTMENTS);
        yearBox.setValue(0);
        semesterBox.setValue(0);
        typeBox.setValue(ALL_TYPES);
        searchField.clear();
    }

    private void buildDetailsPane() {
        detailsPane.getStyleClass().addAll("card", "details-pane");
        detailsPane.setPrefWidth(300);
        detailsPane.setMinWidth(260);
        detailsTitle.getStyleClass().add("details-title");
        detailsTitle.setWrapText(true);

        Button add = Ui.button(I18n.t("add_on_day"), FontAwesomeSolid.PLUS, "ghost-button");
        add.setOnAction(e -> openNew(selected));

        ScrollPane scroll = new ScrollPane(detailsList);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        detailsPane.getChildren().addAll(detailsTitle, add, scroll);
    }

    private Node buildLegend() {
        HBox legend = new HBox(16);
        legend.setAlignment(Pos.CENTER_LEFT);
        for (EventType t : EventType.values()) {
            Circle dot = new Circle(5, Color.web(t.getColorHex()));
            Label name = new Label(I18n.typeLabel(t));
            name.getStyleClass().add("legend-label");
            HBox item = new HBox(5, dot, name);
            item.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(item);
        }
        return legend;
    }

    // ---------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------

    private void move(int direction) {
        if (!searchField.getText().isBlank()) {
            anchor = anchor.plusYears(direction);          // search results cover a whole year
        } else if (mode == ViewMode.MONTH) {
            anchor = anchor.plusMonths(direction);
        } else if (mode == ViewMode.WEEK) {
            anchor = anchor.plusWeeks(direction);
        } else {
            anchor = anchor.plusDays(30L * direction);
        }
        selected = anchor;
        refresh();
    }

    // ---------------------------------------------------------------
    // Loading and drawing
    // ---------------------------------------------------------------

    private void refresh() {
        boolean searching = !searchField.getText().isBlank();
        ViewMode effective = searching ? ViewMode.AGENDA : mode;
        YearMonth ym = YearMonth.from(anchor);

        LocalDate from;
        LocalDate to;
        if (searching) {
            from = LocalDate.of(anchor.getYear(), 1, 1);
            to = LocalDate.of(anchor.getYear(), 12, 31);
        } else if (effective == ViewMode.MONTH) {
            from = MonthGridView.gridStart(ym);
            to = MonthGridView.gridEnd(ym);
        } else if (effective == ViewMode.WEEK) {
            from = DateUtil.startOfWeek(anchor);
            to = from.plusDays(6);
        } else {
            from = anchor;
            to = anchor.plusDays(89);
        }

        try {
            loaded = ctx.getEvents().find(ctx.getUser(), buildFilter(from, to));
        } catch (RuntimeException e) {
            loaded = List.of();
            Ui.error(Ui.userMessage(e));
        }
        if (selected.isBefore(from) || selected.isAfter(to)) {
            selected = anchor;
        }

        monthView = null;
        weekView = null;
        Node view;
        if (effective == ViewMode.MONTH) {
            monthView = new MonthGridView(ctx, ym, loaded, selected,
                    this::onDaySelected, this::openNew, this::openEvent);
            view = monthView;
        } else if (effective == ViewMode.WEEK) {
            weekView = new WeekGridView(ctx, from, loaded, selected, this::onDaySelected, this::openEvent);
            view = weekView;
        } else {
            view = new AgendaListView(ctx, from, to, loaded, this::openEvent);
        }
        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(effective != ViewMode.AGENDA);
        centerHolder.getChildren().setAll(scroll);

        if (searching) {
            titleLabel.setText(I18n.f("search_results_title", I18n.number(anchor.getYear())));
        } else if (effective == ViewMode.MONTH) {
            titleLabel.setText(I18n.monthYear(ym));
        } else if (effective == ViewMode.WEEK) {
            titleLabel.setText(I18n.dayMonth(from) + " – " + I18n.dayMonth(to) + ", " + I18n.number(to.getYear()));
        } else {
            titleLabel.setText(I18n.t("agenda_title"));
        }
        subtitleLabel.setText(new CalendarMonthDao().find(ym.getYear(), ym.getMonthValue())
                .map(m -> I18n.t("bangla") + ": " + m.banglaMonths()
                        + "     " + I18n.t("hijri") + ": " + m.hijriMonths())
                .orElse(""));

        detailsPane.setVisible(effective != ViewMode.AGENDA);
        detailsPane.setManaged(effective != ViewMode.AGENDA);
        renderDetails();
    }

    private EventFilter buildFilter(LocalDate from, LocalDate to) {
        EventFilter filter = new EventFilter().from(from).to(to)
                .department(Ui.deptIdOrNull(deptBox.getValue()))
                .year(Ui.zeroToNull(yearBox.getValue()))
                .semester(Ui.zeroToNull(semesterBox.getValue()))
                .keyword(searchField.getText());
        TypeOption type = typeBox.getValue();
        if (type != null) {
            filter.type(type.type());
        }
        return filter;
    }

    /** The right-hand panel: full date (English | Bangla | Hijri) and the events of the selected day. */
    private void renderDetails() {
        detailsTitle.setText(I18n.fullDate(selected));
        detailsList.getChildren().clear();

        if (ctx.getPolicy().isOffDay(selected)) {
            Label off = new Label(I18n.t("weekly_off_note"));
            off.getStyleClass().add("muted");
            detailsList.getChildren().add(off);
        }
        List<Event> dayEvents = loaded.stream().filter(e -> e.occursOn(selected)).toList();
        if (dayEvents.isEmpty()) {
            Label none = new Label(I18n.t("no_events_day"));
            none.getStyleClass().add("muted");
            detailsList.getChildren().add(none);
        }
        for (Event e : dayEvents) {
            detailsList.getChildren().add(new EventCard(e, ctx, this::openEvent));
        }
    }

    private void onDaySelected(LocalDate day) {
        selected = day;
        if (monthView != null) {
            monthView.select(day);
        }
        if (weekView != null) {
            weekView.select(day);
        }
        renderDetails();
    }

    private void openNew(LocalDate day) {
        EventDialogController.showNew(ctx, day, null, null);
    }

    private void openEvent(Event event) {
        EventDialogController.showExisting(ctx, event, null);
    }
}
