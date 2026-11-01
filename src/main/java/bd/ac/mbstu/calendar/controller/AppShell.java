package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.dao.DepartmentDao;
import bd.ac.mbstu.calendar.dao.SettingsDao;
import bd.ac.mbstu.calendar.model.User;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.service.AuthService;
import bd.ac.mbstu.calendar.service.EventService;
import bd.ac.mbstu.calendar.service.WeeklyOffPolicy;
import bd.ac.mbstu.calendar.service.ai.AiAssistant;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the window (Stage) and decides which screen is inside it:
 * the login screen or the main screen. Theme and language changes are saved in the
 * database and the current screen is rebuilt, so every label and colour updates.
 */
public class AppShell {

    private static final Logger log = LoggerFactory.getLogger(AppShell.class);

    private final Stage stage;
    private final SettingsDao settings = new SettingsDao();
    private final AuthService auth = new AuthService();

    private Scene scene;
    private AppContext context;      // null while nobody is logged in
    private MainController main;     // null while the login screen is showing

    public AppShell(Stage stage) {
        this.stage = stage;
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
    }

    /** Loads the saved theme and language, then shows the login screen. */
    public void start() {
        try {
            Theme.set(settings.get("theme", Theme.LIGHT));
            I18n.setLanguage(settings.get("language", "en"));
        } catch (RuntimeException e) {
            log.warn("Could not read saved settings: {}", e.getMessage());
        }
        showLogin();
        stage.show();
    }

    public AuthService getAuth() {
        return auth;
    }

    // ---------------------------------------------------------------
    // Switching screens
    // ---------------------------------------------------------------

    public void showLogin() {
        disposeMain();
        show(new LoginController(this).getView());
    }

    /** Called by the login screen after a successful sign-in. */
    public void onLoggedIn(User user) {
        context = new AppContext(user, new EventService(), AiAssistant.createDefault(),
                new DepartmentDao().findAll(), WeeklyOffPolicy.load());
        showMain(MainController.PAGE_CALENDAR);
    }

    public void logout() {
        disposeMain();
        auth.logout();
        context = null;
        showLogin();
    }

    private void showMain(int page) {
        disposeMain();
        main = new MainController(context, this, page);
        show(main.getView());
    }

    // ---------------------------------------------------------------
    // Theme and language
    // ---------------------------------------------------------------

    public void toggleTheme() {
        Theme.set(Theme.isDark() ? Theme.LIGHT : Theme.DARK);
        save("theme", Theme.current());
        rebuild();
    }

    public void toggleLanguage() {
        I18n.setLanguage(I18n.isBangla() ? "en" : "bn");
        save("language", I18n.getLanguage());
        rebuild();
    }

    /** Draws the current screen again with the new theme or language (same page). */
    private void rebuild() {
        if (main != null && context != null) {
            showMain(main.getPage());
        } else {
            showLogin();
        }
    }

    private void save(String key, String value) {
        try {
            settings.set(key, value);
        } catch (RuntimeException e) {
            log.warn("Could not save setting {}: {}", key, e.getMessage());
        }
    }

    // ---------------------------------------------------------------

    public void shutdown() {
        disposeMain();
    }

    private void disposeMain() {
        if (main != null) {
            main.dispose();
            main = null;
        }
    }

    private void show(Parent root) {
        if (scene == null) {
            scene = new Scene(root, 1280, 800);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        Theme.apply(scene);
        stage.setTitle(I18n.t("app_title") + " | MBSTU");
    }
}
