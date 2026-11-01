package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.service.ConflictService;
import bd.ac.mbstu.calendar.service.EventFactory;
import bd.ac.mbstu.calendar.service.ai.AiAssistant;
import bd.ac.mbstu.calendar.service.ai.AiEventMapper;
import bd.ac.mbstu.calendar.service.ai.AiResult;
import bd.ac.mbstu.calendar.service.ai.ParsedEvent;
import bd.ac.mbstu.calendar.util.Async;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import bd.ac.mbstu.calendar.util.ValidationException;
import bd.ac.mbstu.calendar.view.Ui;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The "Add / Edit / View event" window.
 *  - New event:        showNew(...)       (optionally pre-filled, e.g. from the AI notice import)
 *  - Existing event:   showExisting(...)  (editable only if the user may modify it, otherwise read-only)
 * On Save we check the input, show conflict warnings ("Save anyway?"), then call EventService.
 * The calendar refreshes by itself through the EventBus (Observer pattern).
 */
public final class EventDialogController {

    private static final Pattern TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}");
    private static final List<Integer> REMINDER_CHOICES = List.of(0, 60, 1440);

    private final AppContext ctx;
    private final Event existing;       // null when adding a new event
    private final Runnable onSaved;     // optional: called after a successful save or delete
    private final boolean editable;
    private boolean populating;         // true while we fill the form from code

    private final TextField quickField = new TextField();
    private final Label quickStatus = new Label();
    private final TextField titleField = new TextField();
    private final TextField titleBnField = new TextField();
    private final ComboBox<EventType> typeBox = new ComboBox<>();
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final TextField startTime = new TextField();
    private final TextField endTime = new TextField();
    private final ComboBox<Department> deptBox = new ComboBox<>();
    private final ComboBox<Integer> yearBox = new ComboBox<>();
    private final ComboBox<Integer> semesterBox = new ComboBox<>();
    private final TextField courseField = new TextField();
    private final TextField locationField = new TextField();
    private final ComboBox<Integer> reminderBox = new ComboBox<>();
    private final TextArea descriptionArea = new TextArea();
    private final CheckBox classOffBox = new CheckBox();
    private final CheckBox officeOffBox = new CheckBox();
    private final CheckBox personalBox = new CheckBox();
    private final Label errorLabel = new Label();

    private EventDialogController(AppContext ctx, Event existing, Runnable onSaved) {
        this.ctx = ctx;
        this.existing = existing;
        this.onSaved = onSaved;
        this.editable = existing == null || ctx.canModify(existing);
    }

    /** Add a new event. prefill may be null. */
    public static void showNew(AppContext ctx, LocalDate date, Event prefill, Runnable onSaved) {
        new EventDialogController(ctx, null, onSaved).open(date, prefill);
    }

    /** Open an existing event (editable or read-only depending on the user's role). */
    public static void showExisting(AppContext ctx, Event event, Runnable onSaved) {
        new EventDialogController(ctx, event, onSaved).open(null, null);
    }

    // ---------------------------------------------------------------
    // Building the dialog
    // ---------------------------------------------------------------

    private void open(LocalDate date, Event prefill) {
        configureControls();
        if (existing != null) {
            populate(existing);
        } else if (prefill != null) {
            populate(prefill);
        } else {
            populateDefaults(date != null ? date : LocalDate.now());
        }
        if (!editable) {
            lockForm();
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? I18n.t("dlg_add_event")
                : editable ? I18n.t("dlg_edit_event") : I18n.t("dlg_view_event"));
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(Theme.stylesheets());
        pane.setMinWidth(620);
        pane.setContent(buildContent());

        if (editable) {
            ButtonType saveType = new ButtonType(I18n.t("save"), ButtonBar.ButtonData.OK_DONE);
            ButtonType deleteType = new ButtonType(I18n.t("delete"), ButtonBar.ButtonData.LEFT);
            ButtonType cancelType = new ButtonType(I18n.t("cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
            pane.getButtonTypes().add(saveType);
            if (existing != null) {
                pane.getButtonTypes().add(deleteType);
            }
            pane.getButtonTypes().add(cancelType);

            // If saving fails we consume the click, so the dialog stays open and shows the error.
            pane.lookupButton(saveType).addEventFilter(ActionEvent.ACTION, ev -> {
                if (!trySave()) {
                    ev.consume();
                }
            });
            if (existing != null) {
                pane.lookupButton(deleteType).addEventFilter(ActionEvent.ACTION, ev -> {
                    if (!tryDelete()) {
                        ev.consume();
                    }
                });
            }
        } else {
            pane.getButtonTypes().add(new ButtonType(I18n.t("close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        }
        dialog.showAndWait();
    }

    private Node buildContent() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(4));
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(130);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);

        int row = 0;
        if (existing == null) {
            grid.add(buildQuickFill(), 0, row++, 2, 1);
        }
        row = addRow(grid, row, "f_title", titleField);
        row = addRow(grid, row, "f_title_bn", titleBnField);
        row = addRow(grid, row, "f_type", typeBox);
        row = addRow(grid, row, "f_start_date", startDate);
        row = addRow(grid, row, "f_end_date", endDate);
        row = addRow(grid, row, "f_time", new HBox(8, startTime, new Label("–"), endTime));
        row = addRow(grid, row, "f_department", deptBox);
        row = addRow(grid, row, "f_year", yearBox);
        row = addRow(grid, row, "f_semester", semesterBox);
        row = addRow(grid, row, "f_course", courseField);
        row = addRow(grid, row, "f_location", locationField);
        row = addRow(grid, row, "f_reminder", reminderBox);
        row = addRow(grid, row, "f_description", descriptionArea);

        if (editable && ctx.isAdmin()) {
            row = addRow(grid, row, "f_class_off", new HBox(20, classOffBox,
                    new Label(I18n.t("f_office_off")), officeOffBox));
            if (existing == null) {
                grid.add(personalBox, 1, row++);
            }
        } else if (existing == null) {
            Label note = new Label(I18n.t("personal_note"));
            note.getStyleClass().add("muted");
            note.setWrapText(true);
            grid.add(note, 0, row++, 2, 1);
        }

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(600);
        scroll.setPrefViewportHeight(480);

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        return new VBox(8, scroll, errorLabel);
    }

    private int addRow(GridPane grid, int row, String labelKey, Node field) {
        grid.add(new Label(I18n.t(labelKey)), 0, row);
        grid.add(field, 1, row);
        return row + 1;
    }

    private Node buildQuickFill() {
        quickField.setPromptText(I18n.t("quick_prompt"));
        Button fill = Ui.button(I18n.t("btn_ai_fill"), FontAwesomeSolid.MAGIC, "ghost-button");
        HBox.setHgrow(quickField, Priority.ALWAYS);
        quickStatus.setWrapText(true);
        quickStatus.getStyleClass().add("muted");

        fill.setOnAction(e -> aiFill(fill));
        quickField.setOnAction(e -> aiFill(fill));
        return new VBox(4, new HBox(8, quickField, fill), quickStatus);
    }

    // ---------------------------------------------------------------
    // Controls: items, converters, listeners
    // ---------------------------------------------------------------

    private void configureControls() {
        typeBox.setItems(FXCollections.observableArrayList(EventType.values()));
        typeBox.setConverter(Ui.converter(I18n::typeLabel));
        deptBox.setItems(Ui.departmentItems(ctx, true));
        deptBox.setConverter(Ui.departmentConverter());
        yearBox.setItems(FXCollections.observableArrayList(0, 1, 2, 3, 4));
        yearBox.setConverter(Ui.yearConverter());
        semesterBox.setItems(FXCollections.observableArrayList(0, 1, 2));
        semesterBox.setConverter(Ui.semesterConverter());
        reminderBox.setItems(FXCollections.observableArrayList(REMINDER_CHOICES));
        reminderBox.setConverter(Ui.converter(m -> m == 0 ? I18n.t("reminder_none")
                : I18n.f("reminder_before", I18n.minutesText(m))));

        startTime.setPromptText("HH:mm");
        endTime.setPromptText("HH:mm");
        startTime.setPrefColumnCount(6);
        endTime.setPrefColumnCount(6);
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);

        for (Region r : new Region[]{titleField, titleBnField, typeBox, startDate, endDate,
                deptBox, yearBox, semesterBox, courseField, locationField, reminderBox, descriptionArea}) {
            r.setMaxWidth(Double.MAX_VALUE);
        }

        // End date follows the start date unless it is already later.
        startDate.valueProperty().addListener((o, oldDate, newDate) -> {
            if (newDate != null && (endDate.getValue() == null || endDate.getValue().isBefore(newDate))) {
                endDate.setValue(newDate);
            }
        });
        // New event: choosing a type fills sensible defaults (FACTORY pattern).
        typeBox.valueProperty().addListener((o, oldType, newType) -> {
            if (!populating && existing == null && newType != null) {
                applyTypeDefaults(newType);
            }
        });
        // Personal events never switch classes or offices off.
        personalBox.selectedProperty().addListener((o, was, isPersonal) -> {
            classOffBox.setDisable(isPersonal);
            officeOffBox.setDisable(isPersonal);
            if (isPersonal) {
                classOffBox.setSelected(false);
                officeOffBox.setSelected(false);
            }
        });
        personalBox.setText(I18n.t("f_personal"));
    }

    private void applyTypeDefaults(EventType type) {
        Event defaults = EventFactory.create(type, "x", LocalDate.now(), LocalDate.now());
        classOffBox.setSelected(defaults.isClassOff());
        officeOffBox.setSelected(defaults.isOfficeOff());
        reminderBox.setValue(defaults.getReminderMinutes() == null ? 0 : defaults.getReminderMinutes());
    }

    /** View-only mode: nothing can be changed. */
    private void lockForm() {
        for (Node n : new Node[]{titleField, titleBnField, typeBox, startDate, endDate, startTime, endTime,
                deptBox, yearBox, semesterBox, courseField, locationField, reminderBox, descriptionArea,
                classOffBox, officeOffBox}) {
            n.setDisable(true);
            n.getStyleClass().add("view-only");
        }
    }

    // ---------------------------------------------------------------
    // Filling the form
    // ---------------------------------------------------------------

    private void populateDefaults(LocalDate date) {
        populating = true;
        try {
            typeBox.setValue(EventType.CLASS_TEST);
            startDate.setValue(date);
            endDate.setValue(date);
            // Students and teachers start with their own department, year and semester.
            deptBox.setValue(ctx.findDepartment(ctx.getUser().getDepartmentId()).orElse(Ui.ALL_DEPARTMENTS));
            yearBox.setValue(ctx.getUser().getYear() == null ? 0 : ctx.getUser().getYear());
            semesterBox.setValue(ctx.getUser().getSemester() == null ? 0 : ctx.getUser().getSemester());
            applyTypeDefaults(EventType.CLASS_TEST);
        } finally {
            populating = false;
        }
    }

    private void populate(Event e) {
        populating = true;
        try {
            typeBox.setValue(e.getType());
            titleField.setText(e.getTitle() == null ? "" : e.getTitle());
            titleBnField.setText(e.getTitleBn() == null ? "" : e.getTitleBn());
            startDate.setValue(e.getStartDate());
            endDate.setValue(e.getEndDate());
            startTime.setText(e.getStartTime() == null ? "" : e.getStartTime().toString());
            endTime.setText(e.getEndTime() == null ? "" : e.getEndTime().toString());
            deptBox.setValue(ctx.findDepartment(e.getDepartmentId()).orElse(Ui.ALL_DEPARTMENTS));
            yearBox.setValue(e.getYear() == null ? 0 : e.getYear());
            semesterBox.setValue(e.getSemester() == null ? 0 : e.getSemester());
            courseField.setText(e.getCourseCode() == null ? "" : e.getCourseCode());
            locationField.setText(e.getLocation() == null ? "" : e.getLocation());
            descriptionArea.setText(e.getDescription() == null ? "" : e.getDescription());
            classOffBox.setSelected(e.isClassOff());
            officeOffBox.setSelected(e.isOfficeOff());

            int reminder = e.getReminderMinutes() == null ? 0 : e.getReminderMinutes();
            if (!reminderBox.getItems().contains(reminder)) {
                reminderBox.getItems().add(reminder); // keep unusual values from imported data
            }
            reminderBox.setValue(reminder);
        } finally {
            populating = false;
        }
    }

    // ---------------------------------------------------------------
    // AI fill (natural-language event creation)
    // ---------------------------------------------------------------

    private void aiFill(Button fill) {
        String text = quickField.getText();
        if (text == null || text.isBlank()) {
            quickStatus.setText(I18n.t("quick_empty"));
            return;
        }
        fill.setDisable(true);
        quickStatus.setText(I18n.t("ai_working"));
        AiAssistant ai = ctx.getAi();
        Async.run(() -> ai.parseEvent(ctx.getUser(), text),
                result -> {
                    fill.setDisable(false);
                    applyParsed(result);
                },
                err -> {
                    fill.setDisable(false);
                    quickStatus.setText(Ui.userMessage(err));
                });
    }

    private void applyParsed(AiResult<ParsedEvent> result) {
        ParsedEvent parsed = result.value();
        populate(AiEventMapper.toEvent(parsed, ctx.getDepartments()));

        List<String> notes = new ArrayList<>();
        notes.add(I18n.f("quick_done", result.provider()));
        if (result.warning() != null) {
            notes.add(result.warning());
        }
        notes.addAll(parsed.warnings());
        quickStatus.setText(String.join("\n", notes));
    }

    // ---------------------------------------------------------------
    // Saving and deleting
    // ---------------------------------------------------------------

    private boolean trySave() {
        errorLabel.setText("");
        try {
            Event event = collect();

            // Warnings (clash, holiday, weekly off). The user may still choose to save.
            List<ConflictService.Conflict> conflicts = ctx.getEvents().previewConflicts(event);
            if (!conflicts.isEmpty()) {
                String lines = conflicts.stream().map(c -> "• " + c.message()).collect(Collectors.joining("\n"));
                boolean saveAnyway = Ui.confirm(I18n.t("conflict_title"),
                        I18n.t("conflict_intro") + "\n\n" + lines + "\n\n" + I18n.t("conflict_question"));
                if (!saveAnyway) {
                    return false;
                }
            }

            if (existing == null) {
                ctx.getEvents().create(ctx.getUser(), event);
            } else {
                ctx.getEvents().update(ctx.getUser(), event);
            }
            if (onSaved != null) {
                onSaved.run();
            }
            return true;
        } catch (RuntimeException ex) {
            errorLabel.setText(Ui.userMessage(ex));
            return false;
        }
    }

    private boolean tryDelete() {
        errorLabel.setText("");
        boolean sure = Ui.confirm(I18n.t("confirm_delete_title"),
                I18n.f("confirm_delete_msg", existing.getTitle()));
        if (!sure) {
            return false;
        }
        try {
            ctx.getEvents().delete(ctx.getUser(), existing.getId());
            if (onSaved != null) {
                onSaved.run();
            }
            return true;
        } catch (RuntimeException ex) {
            errorLabel.setText(Ui.userMessage(ex));
            return false;
        }
    }

    /** Reads the form into a NEW Event object (the saved one is never changed directly). */
    private Event collect() {
        Event e = new Event();
        if (existing != null) {
            e.setId(existing.getId());
            e.setOwnerUserId(existing.getOwnerUserId());
        } else if (!ctx.isAdmin() || personalBox.isSelected()) {
            e.setOwnerUserId(ctx.getUser().getId()); // personal event
        }

        e.setTitle(titleField.getText());
        e.setTitleBn(blankToNull(titleBnField.getText()));
        e.setType(typeBox.getValue());
        e.setStartDate(startDate.getValue());
        e.setEndDate(endDate.getValue());
        e.setStartTime(parseTime(startTime.getText()));
        e.setEndTime(parseTime(endTime.getText()));
        e.setDepartmentId(Ui.deptIdOrNull(deptBox.getValue()));
        e.setYear(Ui.zeroToNull(yearBox.getValue()));
        e.setSemester(Ui.zeroToNull(semesterBox.getValue()));
        e.setCourseCode(blankToNull(courseField.getText()));
        e.setLocation(blankToNull(locationField.getText()));
        e.setDescription(blankToNull(descriptionArea.getText()));
        e.setReminderMinutes(Ui.zeroToNull(reminderBox.getValue()));

        boolean official = e.getOwnerUserId() == null;
        e.setClassOff(ctx.isAdmin() && official && classOffBox.isSelected());
        e.setOfficeOff(ctx.isAdmin() && official && officeOffBox.isSelected());
        return e;
    }

    private static String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }

    /** "10:30" or "9:05" gives a time, blank gives null, anything else is an error message for the user. */
    private static LocalTime parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        if (!TIME_PATTERN.matcher(t).matches()) {
            throw new ValidationException(I18n.t("time_invalid"));
        }
        if (t.indexOf(':') == 1) {
            t = "0" + t;
        }
        try {
            return LocalTime.parse(t);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(I18n.t("time_invalid"));
        }
    }
}
