package bd.ac.mbstu.calendar.view;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.util.I18n;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A plain list grouped by date. An event is listed on its start date
 * (or on the first visible day if it started earlier).
 */
public class AgendaListView extends VBox {

    public AgendaListView(AppContext ctx, LocalDate from, LocalDate to, List<Event> events,
                          Consumer<Event> onOpen) {
        super(8);
        getStyleClass().add("agenda-view");

        if (events.isEmpty()) {
            Label none = new Label(I18n.t("no_events"));
            none.getStyleClass().add("muted");
            getChildren().add(none);
            return;
        }

        Map<LocalDate, List<Event>> groups = new TreeMap<>();
        for (Event e : events) {
            LocalDate key = e.getStartDate().isBefore(from) ? from : e.getStartDate();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<LocalDate, List<Event>> group : groups.entrySet()) {
            Label date = new Label(I18n.fullDate(group.getKey()));
            date.getStyleClass().add("agenda-date");
            getChildren().add(date);
            for (Event e : group.getValue()) {
                getChildren().add(new EventCard(e, ctx, onOpen));
            }
        }
    }
}
