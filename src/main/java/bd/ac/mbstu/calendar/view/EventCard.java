package bd.ac.mbstu.calendar.view;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** One event drawn as a card: coloured bar, title, details, badges. Click = open. */
public class EventCard extends HBox {

    public EventCard(Event event, AppContext ctx, Consumer<Event> onOpen) {
        super(10);
        getStyleClass().add("event-card");
        setAlignment(Pos.CENTER_LEFT);
        setCursor(Cursor.HAND);

        Region bar = new Region();
        bar.setMinWidth(5);
        bar.setPrefWidth(5);
        bar.setMaxWidth(5);
        bar.setStyle("-fx-background-color: " + event.getType().getColorHex() + "; -fx-background-radius: 3;");

        Label title = new Label(event.getTitle(I18n.getLanguage()));
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(metaText(event, ctx));
        meta.getStyleClass().add("card-meta");
        meta.setWrapText(true);

        VBox body = new VBox(3, title, meta);

        FlowPane badges = new FlowPane(4, 4);
        if (event.isClassOff()) {
            badges.getChildren().add(badge(I18n.t("badge_class_off"), "badge-off"));
        }
        if (event.isOfficeOff()) {
            badges.getChildren().add(badge(I18n.t("badge_office_off"), "badge-off"));
        }
        if (event.isPersonal()) {
            badges.getChildren().add(badge(I18n.t("badge_personal"), "badge-personal"));
        }
        if (!badges.getChildren().isEmpty()) {
            body.getChildren().add(badges);
        }

        HBox.setHgrow(body, Priority.ALWAYS);
        getChildren().addAll(bar, body);
        setOnMouseClicked(e -> onOpen.accept(event));
        Tooltip.install(this, new Tooltip(event.getTitle(I18n.getLanguage()) + "\n" + meta.getText()));
    }

    private static Label badge(String text, String styleClass) {
        Label l = new Label(text);
        l.getStyleClass().addAll("badge", styleClass);
        return l;
    }

    /** "Class Test · 10:00–11:00 · ICT · Year 2 · Semester 2 · Room 301" */
    private static String metaText(Event e, AppContext ctx) {
        List<String> parts = new ArrayList<>();
        parts.add(I18n.typeLabel(e.getType()));
        if (e.isMultiDay()) {
            parts.add(I18n.dayMonth(e.getStartDate()) + " – " + I18n.dayMonth(e.getEndDate()));
        }
        if (e.getStartTime() != null) {
            parts.add(e.getStartTime() + (e.getEndTime() != null ? "–" + e.getEndTime() : ""));
        }
        String dept = ctx.departmentCode(e.getDepartmentId());
        if (dept != null) {
            parts.add(dept);
        }
        if (e.getYear() != null) {
            parts.add(I18n.f("year_n", I18n.number(e.getYear())));
        }
        if (e.getSemester() != null) {
            parts.add(I18n.f("semester_n", I18n.number(e.getSemester())));
        }
        if (e.getLocation() != null && !e.getLocation().isBlank()) {
            parts.add(e.getLocation());
        }
        return String.join(" · ", parts);
    }
}
