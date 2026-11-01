package bd.ac.mbstu.calendar.view;

import bd.ac.mbstu.calendar.model.Event;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.util.BanglaDate;
import bd.ac.mbstu.calendar.util.DateUtil;
import bd.ac.mbstu.calendar.util.HijriDate;
import bd.ac.mbstu.calendar.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The month grid: weekday header + 4 to 6 rows of day cells (the week starts on Sunday).
 * Each cell shows the English day number, the Bangla and Hijri day numbers (small),
 * and up to 3 coloured event chips.
 * Click a cell = select it, double-click = add an event, click a chip = open the event.
 */
public class MonthGridView extends VBox {

    private static final int MAX_CHIPS = 3;
    private static final DayOfWeek[] ORDER = {
        DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};

    private final Map<LocalDate, VBox> cells = new HashMap<>();
    private LocalDate selected;

    public MonthGridView(AppContext ctx, YearMonth month, List<Event> events, LocalDate selected,
                         Consumer<LocalDate> onSelect, Consumer<LocalDate> onAdd, Consumer<Event> onOpen) {
        getStyleClass().add("month-view");

        LocalDate start = gridStart(month);
        int rows = rowCount(month);
        Map<LocalDate, List<Event>> byDay = Ui.groupByDay(events, start, gridEnd(month));

        GridPane header = new GridPane();
        GridPane grid = new GridPane();
        for (int c = 0; c < 7; c++) {
            header.getColumnConstraints().add(column());
            grid.getColumnConstraints().add(column());

            Label name = new Label(I18n.dayShort(ORDER[c]));
            name.setMaxWidth(Double.MAX_VALUE);
            name.getStyleClass().add("weekday-header");
            if (ctx.getPolicy().getOffDays().contains(ORDER[c])) {
                name.getStyleClass().add("weekday-off");
            }
            header.add(name, c, 0);
        }

        for (int r = 0; r < rows; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(104);
            rc.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(rc);
            for (int c = 0; c < 7; c++) {
                LocalDate day = start.plusDays(r * 7L + c);
                VBox cell = buildCell(ctx, month, day, byDay.getOrDefault(day, List.of()),
                        onSelect, onAdd, onOpen);
                cells.put(day, cell);
                grid.add(cell, c, r);
            }
        }

        VBox.setVgrow(grid, Priority.ALWAYS);
        getChildren().addAll(header, grid);
        select(selected);
    }

    /** Highlights a day without rebuilding the whole grid. */
    public void select(LocalDate day) {
        VBox old = cells.get(selected);
        if (old != null) {
            old.getStyleClass().remove("selected");
        }
        selected = day;
        VBox now = cells.get(day);
        if (now != null && !now.getStyleClass().contains("selected")) {
            now.getStyleClass().add("selected");
        }
    }

    // ---------------------------------------------------------------
    // Grid range (the controller needs these to load the right events)
    // ---------------------------------------------------------------

    public static LocalDate gridStart(YearMonth month) {
        return DateUtil.startOfWeek(month.atDay(1));
    }

    public static int rowCount(YearMonth month) {
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7; // Sunday = 0
        return (int) Math.ceil((offset + month.lengthOfMonth()) / 7.0);
    }

    public static LocalDate gridEnd(YearMonth month) {
        return gridStart(month).plusDays(rowCount(month) * 7L - 1);
    }

    // ---------------------------------------------------------------

    private static ColumnConstraints column() {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setPercentWidth(100.0 / 7);
        return cc;
    }

    private static VBox buildCell(AppContext ctx, YearMonth month, LocalDate day, List<Event> dayEvents,
                                  Consumer<LocalDate> onSelect, Consumer<LocalDate> onAdd,
                                  Consumer<Event> onOpen) {
        VBox cell = new VBox(2);
        cell.getStyleClass().add("day-cell");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);

        if (!YearMonth.from(day).equals(month)) {
            cell.getStyleClass().add("other-month");
        }
        if (day.equals(LocalDate.now())) {
            cell.getStyleClass().add("today");
        }
        if (ctx.getPolicy().isOffDay(day)) {
            cell.getStyleClass().add("weekly-off");
        }
        if (dayEvents.stream().anyMatch(Event::isClassOff)) {
            cell.getStyleClass().add("class-off");
        }

        Label number = new Label(I18n.number(day.getDayOfMonth()));
        number.getStyleClass().add("day-number");
        Label alt = new Label(altText(day));
        alt.getStyleClass().add("day-alt");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(number, spacer, alt);
        head.setAlignment(Pos.CENTER_LEFT);
        Tooltip.install(head, new Tooltip(I18n.fullDate(day)));
        cell.getChildren().add(head);

        int shown = 0;
        for (Event e : dayEvents) {
            if (shown == MAX_CHIPS) {
                break;
            }
            Label chip = new Label(e.getTitle(I18n.getLanguage()));
            chip.getStyleClass().add("event-chip");
            chip.setStyle(Ui.chipStyle(e.getType()));
            chip.setMaxWidth(Double.MAX_VALUE);
            chip.setTooltip(new Tooltip(e.getTitle(I18n.getLanguage()) + "\n" + I18n.typeLabel(e.getType())));
            chip.setOnMouseClicked(ev -> {
                onOpen.accept(e);
                ev.consume(); // do not also select the day
            });
            cell.getChildren().add(chip);
            shown++;
        }
        if (dayEvents.size() > MAX_CHIPS) {
            Label more = new Label(I18n.f("more_n", I18n.number(dayEvents.size() - MAX_CHIPS)));
            more.getStyleClass().add("more-label");
            cell.getChildren().add(more);
        }

        cell.setOnMouseClicked(ev -> {
            onSelect.accept(day);
            if (ev.getClickCount() == 2) {
                onAdd.accept(day);
            }
        });
        return cell;
    }

    /** Small "Bangla day · Hijri day" text, for example "১৩ · ১৬". */
    private static String altText(LocalDate day) {
        try {
            return I18n.number(BanglaDate.from(day).day()) + " · " + I18n.number(HijriDate.from(day).day());
        } catch (RuntimeException e) {
            return "";
        }
    }
}
