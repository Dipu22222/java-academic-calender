package bd.ac.mbstu.calendar.view;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.util.BanglaDate;
import bd.ac.mbstu.calendar.util.HijriDate;
import bd.ac.mbstu.calendar.util.I18n;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Seven tall columns (Sunday to Saturday), each with the day's event cards. */
public class WeekGridView extends GridPane {

    private final Map<LocalDate, VBox> columns = new HashMap<>();
    private LocalDate selected;

    public WeekGridView(AppContext ctx, LocalDate weekStart, List<Event> events, LocalDate selected,
                        Consumer<LocalDate> onSelect, Consumer<Event> onOpen) {
        getStyleClass().add("week-view");
        setHgap(8);

        Map<LocalDate, List<Event>> byDay = Ui.groupByDay(events, weekStart, weekStart.plusDays(6));

        RowConstraints row = new RowConstraints();
        row.setVgrow(Priority.ALWAYS);
        getRowConstraints().add(row);

        for (int c = 0; c < 7; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7);
            getColumnConstraints().add(cc);

            LocalDate day = weekStart.plusDays(c);
            VBox column = new VBox(8);
            column.getStyleClass().add("week-column");
            column.setMinWidth(0);
            column.setPrefWidth(0);
            column.setMaxHeight(Double.MAX_VALUE);
            if (ctx.getPolicy().isOffDay(day)) {
                column.getStyleClass().add("week-off-column");
            }

            Label name = new Label(I18n.dayShort(day.getDayOfWeek()));
            name.getStyleClass().add("week-day-name");
            Label number = new Label(I18n.number(day.getDayOfMonth()));
            number.getStyleClass().add("week-day-number");
            Label alt = new Label(altText(day));
            alt.getStyleClass().add("day-alt");
            column.getChildren().addAll(name, number, alt);

            List<Event> dayEvents = byDay.getOrDefault(day, List.of());
            if (dayEvents.isEmpty()) {
                Label none = new Label("—");
                none.getStyleClass().add("muted");
                column.getChildren().add(none);
            }
            for (Event e : dayEvents) {
                column.getChildren().add(new EventCard(e, ctx, onOpen));
            }

            column.setOnMouseClicked(ev -> onSelect.accept(day));
            columns.put(day, column);
            add(column, c, 0);
        }
        select(selected);
    }

    public void select(LocalDate day) {
        VBox old = columns.get(selected);
        if (old != null) {
            old.getStyleClass().remove("selected");
        }
        selected = day;
        VBox now = columns.get(day);
        if (now != null && !now.getStyleClass().contains("selected")) {
            now.getStyleClass().add("selected");
        }
    }

    private static String altText(LocalDate day) {
        try {
            return I18n.number(BanglaDate.from(day).day()) + " · " + I18n.number(HijriDate.from(day).day());
        } catch (RuntimeException e) {
            return "";
        }
    }
}
