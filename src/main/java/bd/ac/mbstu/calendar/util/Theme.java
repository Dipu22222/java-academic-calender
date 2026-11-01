package bd.ac.mbstu.calendar.util;

import javafx.scene.Scene;

import java.util.List;
import java.util.Objects;

/** Which theme is active ("light" or "dark") and which stylesheets belong to it. */
public final class Theme {

    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static String current = LIGHT;

    private Theme() {
    }

    public static String current() {
        return current;
    }

    public static boolean isDark() {
        return DARK.equals(current);
    }

    public static void set(String theme) {
        current = DARK.equals(theme) ? DARK : LIGHT;
    }

    /** colours first (light.css or dark.css), then the shared app.css that uses them. */
    public static List<String> stylesheets() {
        return List.of(resource("/css/" + current + ".css"), resource("/css/app.css"));
    }

    public static void apply(Scene scene) {
        scene.getStylesheets().setAll(stylesheets());
    }

    private static String resource(String path) {
        return Objects.requireNonNull(Theme.class.getResource(path), "Missing resource " + path)
                .toExternalForm();
    }
}
