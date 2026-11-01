package bd.ac.mbstu.calendar;

import bd.ac.mbstu.calendar.controller.AppShell;
import bd.ac.mbstu.calendar.service.SeedLoader;
import bd.ac.mbstu.calendar.util.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point. It opens the database, loads the seed data (holidays, demo users)
 * and hands the window to AppShell, which shows the login screen.
 */
public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private AppShell shell;

    @Override
    public void start(Stage stage) {
        // Anything nobody caught is written to the log instead of vanishing.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                log.error("Uncaught error in thread {}", thread.getName(), error));

        try {
            DatabaseManager.getInstance();      // creates data/calendar.db and the tables
            new SeedLoader().seedAll();         // users + holidays (skipped if already there)
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            new Alert(Alert.AlertType.ERROR, "The application could not start:\n" + e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        shell = new AppShell(stage);
        shell.start();
    }

    @Override
    public void stop() {
        if (shell != null) {
            shell.shutdown();
        }
        try {
            DatabaseManager.getInstance().close();
        } catch (RuntimeException e) {
            log.warn("Could not close the database cleanly: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
