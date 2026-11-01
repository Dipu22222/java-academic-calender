package bd.ac.mbstu.calendar.util;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs slow work (AI calls, bcrypt) on a helper thread so the window never freezes.
 * onSuccess and onError are always called back on the JavaFX UI thread,
 * so they are safe to touch buttons and labels.
 */
public final class Async {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread t = new Thread(runnable, "ui-background");
        t.setDaemon(true); // does not stop the app from closing
        return t;
    });

    private Async() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        POOL.execute(task);
    }
}
