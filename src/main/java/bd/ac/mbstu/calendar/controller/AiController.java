package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.model.EventType;
import bd.ac.mbstu.calendar.model.Role;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.service.ConflictService;
import bd.ac.mbstu.calendar.service.ai.AiEventMapper;
import bd.ac.mbstu.calendar.service.ai.AiResult;
import bd.ac.mbstu.calendar.service.ai.ParsedEvent;
import bd.ac.mbstu.calendar.util.Async;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.view.Ui;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The AI page. Tabs depend on the role:
 *   everyone: Chat, Weekly digest | student: Study plan
 *   admin + teacher: Suggest dates | admin: Notice to events
 * Every AI call runs in the background (Async) so the window never freezes.
 */
public class AiController {

    private static final List<String> SAMPLE_QUESTIONS = List.of(
            "When is the next holiday?",
            "Show class tests next week",
            "How many days until Victory Day?",
            "Is tomorrow a holiday?");

    private final AppContext ctx;
    private final BorderPane root = new BorderPane();

    public AiController(AppContext ctx) {
        this.ctx = ctx;
        root.getStyleClass().add("page");
        root.setPadding(new Insets(16));

        Label provider = new Label(I18n.f("ai_provider", ctx.getAi().describeProvider()));
        provider.getStyleClass().add("muted");
        BorderPane.setMargin(provider, new Insets(0, 0, 10, 0));
        root.setTop(provider);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(tab("tab_chat", buildChatTab()));
        tabs.getTabs().add(tab("tab_digest", buildDigestTab()));
        Role role = ctx.getUser().getRole();
        if (role == Role.STUDENT) {
            tabs.getTabs().add(tab("tab_study", buildStudyTab()));
        }
        if (role == Role.ADMIN || role == Role.TEACHER) {
            tabs.getTabs().add(tab("tab_suggest", buildSuggestTab()));
        }
        if (role == Role.ADMIN) {
            tabs.getTabs().add(tab("tab_notice", buildNoticeTab()));
        }
        root.setCenter(tabs);
    }

    public Node getView() {
        return root;
    }

    private Tab tab(String titleKey, Node content) {
        return new Tab(I18n.t(titleKey), content);
    }

    // ---------------------------------------------------------------
    // 1. Chat
    // ---------------------------------------------------------------

    private Node buildChatTab() {
        VBox messages = new VBox(10);
        messages.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        messages.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0)); // keep the newest visible

        addBubble(messages, I18n.t("chat_welcome"), false);

        TextField input = new TextField();
        input.setPromptText(I18n.t("chat_input"));
        HBox.setHgrow(input, Priority.ALWAYS);
        Button send = Ui.button(I18n.t("send"), FontAwesomeSolid.PAPER_PLANE, "primary-button");

        Runnable submit = () -> {
            String question = input.getText().trim();
            if (question.isEmpty()) {
                return;
            }
            input.clear();
            input.setDisable(true);
            send.setDisable(true);
            addBubble(messages, question, true);
            Label answer = addBubble(messages, I18n.t("ai_thinking"), false);

            Async.run(() -> ctx.getAi().ask(ctx.getUser(), question),
                    result -> {
                        answer.setText(format(result));
                        input.setDisable(false);
                        send.setDisable(false);
                        input.requestFocus();
                    },
                    err -> {
                        answer.setText(Ui.userMessage(err));
                        input.setDisable(false);
                        send.setDisable(false);
                    });
        };
        send.setOnAction(e -> submit.run());
        input.setOnAction(e -> submit.run());

        FlowPane samples = new FlowPane(8, 8);
        for (String sample : SAMPLE_QUESTIONS) {
            Button b = Ui.button(sample, null, "ghost-button");
            b.setOnAction(e -> {
                input.setText(sample);
                submit.run();
            });
            samples.getChildren().add(b);
        }

        return card(scroll, samples, new HBox(8, input, send));
    }

    private Label addBubble(VBox messages, String text, boolean fromUser) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(640);
        bubble.getStyleClass().addAll("bubble", fromUser ? "bubble-user" : "bubble-ai");
        HBox row = new HBox(bubble);
        row.setAlignment(fromUser ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        messages.getChildren().add(row);
        return bubble;
    }

    // ---------------------------------------------------------------
    // 2. Weekly digest
    // ---------------------------------------------------------------

    private Node buildDigestTab() {
        ComboBox<Department> dept = new ComboBox<>(Ui.departmentItems(ctx, true));
        dept.setConverter(Ui.departmentConverter());
        dept.setValue(ctx.findDepartment(ctx.getUser().getDepartmentId()).orElse(Ui.ALL_DEPARTMENTS));
        DatePicker day = new DatePicker(LocalDate.now());
        Button go = Ui.button(I18n.t("generate"), FontAwesomeSolid.MAGIC, "primary-button");
        TextArea out = outputArea();

        go.setOnAction(e -> {
            Integer deptId = Ui.deptIdOrNull(dept.getValue());
            LocalDate date = day.getValue();
            runToArea(go, out, () -> ctx.getAi().weeklyDigest(ctx.getUser(), deptId, date));
        });

        FlowPane controls = new FlowPane(10, 8, new Label(I18n.t("f_department")), dept,
                new Label(I18n.t("week_of")), day, go);
        controls.setAlignment(Pos.CENTER_LEFT);
        return card(controls, out);
    }

    // ---------------------------------------------------------------
    // 3. Study plan (students)
    // ---------------------------------------------------------------

    private Node buildStudyTab() {
        User u = ctx.getUser();
        String group = ctx.findDepartment(u.getDepartmentId()).map(Department::getCode).orElse("-");
        if (u.getYear() != null) {
            group += ", " + I18n.f("year_n", I18n.number(u.getYear()));
        }
        if (u.getSemester() != null) {
            group += ", " + I18n.f("semester_n", I18n.number(u.getSemester()));
        }
        Label info = new Label(I18n.f("study_info", group));
        info.getStyleClass().add("muted");
        info.setWrapText(true);

        Button go = Ui.button(I18n.t("study_btn"), FontAwesomeSolid.MAGIC, "primary-button");
        TextArea out = outputArea();
        go.setOnAction(e -> runToArea(go, out, () -> ctx.getAi().studyPlan(ctx.getUser())));
        return card(info, go, out);
    }

    // ---------------------------------------------------------------
    // 4. Suggest dates (admin + teacher)
    // ---------------------------------------------------------------

    private Node buildSuggestTab() {
        ComboBox<EventType> type = new ComboBox<>(
                FXCollections.observableArrayList(EventType.CLASS_TEST, EventType.EXAM));
        type.setConverter(Ui.converter(I18n::typeLabel));
        type.setValue(EventType.CLASS_TEST);

        ComboBox<Department> dept = new ComboBox<>(Ui.departmentItems(ctx, false));
        dept.setConverter(Ui.departmentConverter());
        dept.setValue(ctx.findDepartment(ctx.getUser().getDepartmentId())
                .orElse(dept.getItems().isEmpty() ? null : dept.getItems().get(0)));

        ComboBox<Integer> year = new ComboBox<>(FXCollections.observableArrayList(0, 1, 2, 3, 4));
        year.setConverter(Ui.yearConverter());
        year.setValue(0);
        ComboBox<Integer> semester = new ComboBox<>(FXCollections.observableArrayList(0, 1, 2));
        semester.setConverter(Ui.semesterConverter());
        semester.setValue(0);

        DatePicker from = new DatePicker(LocalDate.now());
        DatePicker to = new DatePicker(LocalDate.now().plusDays(30));
        Button go = Ui.button(I18n.t("suggest_btn"), FontAwesomeSolid.MAGIC, "primary-button");
        TextArea out = outputArea();

        go.setOnAction(e -> {
            EventType t = type.getValue();
            Integer deptId = Ui.deptIdOrNull(dept.getValue());
            Integer y = Ui.zeroToNull(year.getValue());
            Integer s = Ui.zeroToNull(semester.getValue());
            LocalDate f = from.getValue();
            LocalDate u = to.getValue();
            runToArea(go, out, () -> ctx.getAi().suggestSchedule(ctx.getUser(), t, deptId, y, s, f, u));
        });

        FlowPane controls = new FlowPane(10, 8, type, dept, year, semester,
                new Label(I18n.t("suggest_from")), from, new Label(I18n.t("suggest_to")), to, go);
        controls.setAlignment(Pos.CENTER_LEFT);
        return card(controls, out);
    }

    // ---------------------------------------------------------------
    // 5. Notice to events (admin)
    // ---------------------------------------------------------------

    private Node buildNoticeTab() {
        TextArea notice = new TextArea();
        notice.setWrapText(true);
        notice.setPrefRowCount(7);
        notice.setPromptText(I18n.t("notice_prompt"));

        Button extract = Ui.button(I18n.t("btn_extract"), FontAwesomeSolid.MAGIC, "primary-button");
        Button addSelected = Ui.button(I18n.t("btn_add_selected"), FontAwesomeSolid.CHECK, "primary-button");
        addSelected.setDisable(true);
        Label status = new Label();
        status.setWrapText(true);
        status.getStyleClass().add("muted");

        VBox rowsBox = new VBox(8);
        ScrollPane rowsScroll = new ScrollPane(rowsBox);
        rowsScroll.setFitToWidth(true);
        VBox.setVgrow(rowsScroll, Priority.ALWAYS);
        List<ProposalRow> rows = new ArrayList<>();

        extract.setOnAction(e -> {
            String text = notice.getText();
            extract.setDisable(true);
            status.setText(I18n.t("ai_thinking"));
            rowsBox.getChildren().clear();
            rows.clear();
            addSelected.setDisable(true);

            Async.run(() -> ctx.getAi().extractFromNotice(ctx.getUser(), text),
                    result -> {
                        extract.setDisable(false);
                        for (ParsedEvent p : result.value()) {
                            ProposalRow row = new ProposalRow(p);
                            rows.add(row);
                            rowsBox.getChildren().add(row.node);
                        }
                        String info = rows.isEmpty() ? I18n.t("notice_none")
                                : I18n.f("notice_found", I18n.number(rows.size()));
                        status.setText(info + (result.warning() != null ? "\n" + result.warning() : ""));
                        addSelected.setDisable(rows.isEmpty());
                    },
                    err -> {
                        extract.setDisable(false);
                        status.setText(Ui.userMessage(err));
                    });
        });

        addSelected.setOnAction(e -> {
            int added = 0;
            int failed = 0;
            for (ProposalRow row : rows) {
                if (row.done || !row.check.isSelected()) {
                    continue;
                }
                if (row.add()) {
                    added++;
                } else {
                    failed++;
                }
            }
            status.setText(I18n.f("added_summary", I18n.number(added), I18n.number(failed)));
        });

        return card(notice, new HBox(10, extract, addSelected), status, rowsScroll);
    }

    /** One proposed event from the notice: tick box, summary, warnings, and an Edit button. */
    private final class ProposalRow {
        final Event event;
        final CheckBox check = new CheckBox();
        final Label statusLabel = new Label();
        final HBox node;
        boolean done;

        ProposalRow(ParsedEvent parsed) {
            event = AiEventMapper.toEvent(parsed, ctx.getDepartments());

            List<String> notes = new ArrayList<>(parsed.warnings());
            boolean needsFix = false;
            try {
                for (ConflictService.Conflict c : ctx.getEvents().previewConflicts(event)) {
                    notes.add(c.message());
                }
            } catch (RuntimeException ex) {
                notes.add(Ui.userMessage(ex)); // for example "Department is required..."
                needsFix = true;
            }

            String dept = ctx.departmentCode(event.getDepartmentId());
            Label summary = new Label(event.getTitle() + "  -  " + I18n.typeLabel(event.getType())
                    + "  -  " + I18n.dateLong(event.getStartDate())
                    + (event.getStartTime() != null ? " " + event.getStartTime() : "")
                    + (dept != null ? "  -  " + dept : ""));
            summary.setWrapText(true);
            statusLabel.setText(String.join("\n", notes));
            statusLabel.setWrapText(true);
            statusLabel.getStyleClass().add("muted");

            Button edit = Ui.button(I18n.t("btn_edit"), FontAwesomeSolid.EDIT, "ghost-button");
            edit.setOnAction(ev -> EventDialogController.showNew(ctx, event.getStartDate(), event, this::markDone));

            check.setSelected(!needsFix);
            VBox text = new VBox(3, summary, statusLabel);
            HBox.setHgrow(text, Priority.ALWAYS);
            node = new HBox(10, check, text, edit);
            node.setAlignment(Pos.CENTER_LEFT);
            node.getStyleClass().add("proposal-row");
        }

        void markDone() {
            done = true;
            check.setSelected(false);
            check.setDisable(true);
            statusLabel.setText(I18n.t("added"));
        }

        /** Saves through EventService (roles + validation). Returns false and shows why if it fails. */
        boolean add() {
            try {
                ctx.getEvents().create(ctx.getUser(), event);
                markDone();
                return true;
            } catch (RuntimeException ex) {
                statusLabel.setText(Ui.userMessage(ex));
                return false;
            }
        }
    }

    // ---------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------

    /** Runs an AI call in the background and prints the answer in a text area. */
    private void runToArea(Button button, TextArea area, Callable<AiResult<String>> call) {
        button.setDisable(true);
        area.setText(I18n.t("ai_thinking"));
        Async.run(call,
                result -> {
                    area.setText(format(result));
                    button.setDisable(false);
                },
                err -> {
                    area.setText(Ui.userMessage(err));
                    button.setDisable(false);
                });
    }

    /** Answer text + a note if the offline fallback was used + who answered. */
    private String format(AiResult<String> result) {
        StringBuilder sb = new StringBuilder(result.value());
        if (result.warning() != null) {
            sb.append("\n\n").append(I18n.t("note")).append(": ").append(result.warning());
        }
        sb.append("\n\n— ").append(result.provider());
        return sb.toString();
    }

    private TextArea outputArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        VBox.setVgrow(area, Priority.ALWAYS);
        return area;
    }

    /** A white card holding the given nodes; a text area or scroll pane at the bottom grows to fill the space. */
    private VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.setPadding(new Insets(14));
        box.getStyleClass().add("card");
        if (children.length > 0) {
            Node last = children[children.length - 1];
            if (last instanceof TextArea || last instanceof ScrollPane) {
                VBox.setVgrow(last, Priority.ALWAYS);
            }
        }
        return box;
    }
}
