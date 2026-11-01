package bd.ac.mbstu.calendar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OBSERVER pattern.
 *  - Screens "subscribe" with a listener.
 *  - When data changes, a service calls publish().
 *  - Every listener runs, so every screen refreshes itself.
 *
 * NOTE: listeners run on the thread that called publish(). In JavaFX
 * we wrap UI updates in Platform.runLater(...).
 */
public final class EventBus {

    public enum ChangeType {
        EVENT_ADDED, EVENT_UPDATED, EVENT_DELETED, DATA_IMPORTED, SETTINGS_CHANGED
    }

    /** One method, so a lambda works:  bus.subscribe(type -> refresh()); */
    @FunctionalInterface
    public interface DataChangeListener {
        void onDataChanged(ChangeType type);
    }

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);
    private static final EventBus INSTANCE = new EventBus();

    private final List<DataChangeListener> listeners = new CopyOnWriteArrayList<>();

    private EventBus() {
    }

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public void subscribe(DataChangeListener listener) {
        listeners.add(listener);
    }

    public void unsubscribe(DataChangeListener listener) {
        listeners.remove(listener);
    }

    public void publish(ChangeType type) {
        log.debug("Data changed: {} ({} listeners)", type, listeners.size());
        for (DataChangeListener listener : listeners) {
            try {
                listener.onDataChanged(type);
            } catch (RuntimeException e) {
                // One broken screen must not stop the others from refreshing.
                log.warn("A listener failed on {}: {}", type, e.getMessage());
            }
        }
    }
}
