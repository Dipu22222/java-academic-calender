package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.util.Async;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import bd.ac.mbstu.calendar.view.Ui;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * The sign-in screen. The password check (BCrypt) runs on a background thread
 * so the window never freezes. On success the AppShell opens the main screen.
 */
public class LoginController {

    private final AppShell shell;
    private final StackPane root = new StackPane();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label errorLabel = new Label();
    private final Button loginButton = new Button();

    public LoginController(AppShell shell) {
        this.shell = shell;
        root.getStyleClass().add("login-root");
        root.setPadding(new Insets(24));

        FontIcon logo = Ui.icon(FontAwesomeSolid.UNIVERSITY, 56);
        logo.getStyleClass().add("login-logo");

        Label title = new Label(I18n.t("app_title"));
        title.getStyleClass().add("login-title");

        Label university = new Label(I18n.t("university"));
        university.getStyleClass().add("login-university");
        university.setWrapText(true);
        university.setTextAlignment(TextAlignment.CENTER);

        Label subtitle = new Label(I18n.t("login_subtitle"));
        subtitle.getStyleClass().add("muted");

        usernameField.setPromptText(I18n.t("username"));
        passwordField.setPromptText(I18n.t("password"));
        usernameField.setOnAction(e -> passwordField.requestFocus());
        passwordField.setOnAction(e -> attemptLogin());

        errorLabel.getStyleClass().add("error-label");
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(Double.MAX_VALUE);

        loginButton.setText(I18n.t("login"));
        loginButton.setGraphic(Ui.icon(FontAwesomeSolid.SIGN_IN_ALT, 14));
        loginButton.getStyleClass().add("primary-button");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(e -> attemptLogin());

        Label hint = new Label(I18n.t("login_hint"));
        hint.getStyleClass().add("login-hint");
        hint.setWrapText(true);
        hint.setTextAlignment(TextAlignment.CENTER);

        VBox card = new VBox(12, logo, title, university, subtitle,
                usernameField, passwordField, errorLabel, loginButton, hint, buildToggles());
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("login-card");
        card.setMaxSize(420, Region.USE_PREF_SIZE);

        root.getChildren().add(card);
        Platform.runLater(usernameField::requestFocus);
    }

    public Parent getView() {
        return root;
    }

    /** Language and theme buttons, so the user can switch before signing in. */
    private Node buildToggles() {
        Button language = Ui.button(I18n.isBangla() ? "English" : "বাংলা", FontAwesomeSolid.LANGUAGE, "ghost-button");
        language.setTooltip(new Tooltip(I18n.t("switch_language")));
        language.setOnAction(e -> shell.toggleLanguage());

        Button theme = Ui.button("", Theme.isDark() ? FontAwesomeSolid.SUN : FontAwesomeSolid.MOON, "ghost-button");
        theme.setTooltip(new Tooltip(I18n.t("switch_theme")));
        theme.setOnAction(e -> shell.toggleTheme());

        HBox row = new HBox(10, language, theme);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private void attemptLogin() {
        errorLabel.setText("");
        String username = usernameField.getText();
        String password = passwordField.getText();
        loginButton.setDisable(true);

        Async.run(() -> shell.getAuth().login(username, password),
                user -> shell.onLoggedIn(user),
                err -> {
                    loginButton.setDisable(false);
                    passwordField.clear();
                    errorLabel.setText(Ui.userMessage(err));
                    passwordField.requestFocus();
                });
    }
}
