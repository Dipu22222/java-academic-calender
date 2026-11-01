package bd.ac.mbstu.calendar.view;

import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.util.ArrayList;
import java.util.List;

/**
 * A small desktop pop-up in the bottom-right corner of the screen.
 * It stays on top of other windows and closes itself after 12 seconds.
 * show() is safe to call from any thread.
 */
public final class NotificationPopup {

    private static final int WIDTH = 340;
    private static final List<Stage> OPEN = new ArrayList<>();

    private NotificationPopup() {
    }

    public static void show(String title, String message) {
        Platform.runLater(() -> display(title, message));
    }

    private static void display(String title, String message) {
        Stage stage = new Stage(StageStyle.UNDECORATED);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("toast-title");
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.getStyleClass().add("toast-message");

        Button close = Ui.iconButton(FontAwesomeSolid.TIMES, I18n.t("close"));
        close.setOnAction(e -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, Ui.icon(FontAwesomeSolid.BELL, 16), titleLabel, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, header, messageLabel);
        box.getStyleClass().add("toast");
        box.setPrefWidth(WIDTH);
        box.setMaxWidth(WIDTH);

        Scene scene = new Scene(box);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);

        int slot = OPEN.size(); // several pop-ups stack upwards
        OPEN.add(stage);
        stage.setOnHidden(e -> OPEN.remove(stage));
        stage.setOnShown(e -> {
            Rectangle2D area = Screen.getPrimary().getVisualBounds();
            stage.setX(area.getMaxX() - stage.getWidth() - 16);
            stage.setY(area.getMaxY() - (stage.getHeight() + 12) * (slot + 1) - 4);
        });
        stage.show();

        PauseTransition wait = new PauseTransition(Duration.seconds(12));
        wait.setOnFinished(e -> stage.close());
        wait.play();
    }
}
