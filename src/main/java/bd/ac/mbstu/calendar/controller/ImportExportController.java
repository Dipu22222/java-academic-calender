package bd.ac.mbstu.calendar.controller;

import bd.ac.mbstu.calendar.model.Department;
import bd.ac.mbstu.calendar.service.AppContext;
import bd.ac.mbstu.calendar.service.io.EventImporter.Candidate;
import bd.ac.mbstu.calendar.service.io.EventImporter.ImportPreview;
import bd.ac.mbstu.calendar.service.io.ImportExportService;
import bd.ac.mbstu.calendar.service.io.ImportExportService.ExportFormat;
import bd.ac.mbstu.calendar.util.Async;
import bd.ac.mbstu.calendar.util.I18n;
import bd.ac.mbstu.calendar.util.Theme;
import bd.ac.mbstu.calendar.view.Ui;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The two admin dialogs: "Import calendar" and "Export calendar".
 * File reading, saving and writing run in the background (Async), so the window never freezes.
 * The real permission check is inside ImportExportService (admin only).
 */
public final class ImportExportController {

    private ImportExportController() {
    }

    // ===============================================================
    // Import
    // ===============================================================

    public static void showImport(AppContext ctx, Window owner) {
        ImportExportService service = new ImportExportService();
        AtomicReference<ImportPreview> current = new AtomicReference<>();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18n.t("import_title"));
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(Theme.stylesheets());
        pane.setPrefSize(780, 580);

        Button choose = Ui.button(I18n.t("choose_file"), FontAwesomeSolid.FOLDER_OPEN, "ghost-button");
        Button template = Ui.button(I18n.t("save_template"), FontAwesomeSolid.DOWNLOAD, "ghost-button");
        Label fileLabel = new Label();
        fileLabel.getStyleClass().add("muted");
        Label hint = new Label(I18n.t("import_hint"));
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        Label summary = new Label();
        summary.setWrapText(true);

        TableView<Candidate> table = new TableView<>();
        table.getColumns().add(column(I18n.t("col_row"), 60, c -> String.valueOf(c.row())));
        table.getColumns().add(column(I18n.t("f_start_date"), 190, c -> c.event().getStartDate()
                + (c.event().isMultiDay() ? " → " + c.event().getEndDate() : "")));
        table.getColumns().add(column(I18n.t("f_title"), 300, c -> c.event().getTitle()));
        table.getColumns().add(column(I18n.t("f_type"), 130, c -> I18n.typeLabel(c.event().getType())));
        table.getColumns().add(column(I18n.t("f_department"), 90, c -> {
            Integer id = c.event().getDepartmentId();
            String code = ctx.departmentCode(id);
            return code == null ? "—" : code;
        }));
        VBox.setVgrow(table, Priority.ALWAYS);

        Label problemsLabel = new Label(I18n.t("import_problems"));
        TextArea problems = new TextArea();
        problems.setEditable(false);
        problems.setPrefRowCount(5);

        ButtonType importType = new ButtonType(I18n.t("import_btn"), ButtonBar.ButtonData.OK_DONE);
        ButtonType closeType = new ButtonType(I18n.t("close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(importType, closeType);
        Button importButton = (Button) pane.lookupButton(importType);
        importButton.setDisable(true);

        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("choose_file"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Calendar files (*.csv, *.json, *.xlsx)", "*.csv", "*.json", "*.xlsx"));
            File file = chooser.showOpenDialog(pane.getScene().getWindow());
            if (file == null) {
                return;
            }
            fileLabel.setText(file.getName());
            summary.setText(I18n.t("ai_working"));
            choose.setDisable(true);
            importButton.setDisable(true);

            Async.run(() -> service.preview(ctx.getUser(), file.toPath()),
                    preview -> {
                        choose.setDisable(false);
                        current.set(preview);
                        table.getItems().setAll(preview.candidates());
                        problems.setText(String.join("\n", preview.errors()));
                        summary.setText(I18n.f("import_summary",
                                I18n.number(preview.candidates().size()), I18n.number(preview.errors().size())));
                        importButton.setDisable(preview.candidates().isEmpty());
                    },
                    err -> {
                        choose.setDisable(false);
                        current.set(null);
                        table.getItems().clear();
                        problems.clear();
                        summary.setText(Ui.userMessage(err));
                    });
        });

        template.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName("calendar_template.csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
            File file = chooser.showSaveDialog(pane.getScene().getWindow());
            if (file == null) {
                return;
            }
            try {
                Files.writeString(file.toPath(), "\uFEFF" + ImportExportService.templateCsv(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                Ui.error(ex.getMessage());
            }
        });

        // Keep the dialog open while the import runs; close it ourselves when it finishes.
        importButton.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            ImportPreview preview = current.get();
            if (preview == null || preview.candidates().isEmpty()) {
                return;
            }
            importButton.setDisable(true);
            choose.setDisable(true);
            summary.setText(I18n.t("ai_working"));

            Async.run(() -> service.commit(ctx.getUser(), preview),
                    result -> {
                        dialog.close();
                        String message = I18n.f("import_done", I18n.number(result.added()),
                                I18n.number(result.duplicates()), I18n.number(result.failures().size()));
                        if (!result.failures().isEmpty()) {
                            message += "\n\n" + String.join("\n", result.failures().stream().limit(10).toList());
                        }
                        Ui.info(I18n.t("import_title"), message);
                    },
                    err -> {
                        choose.setDisable(false);
                        importButton.setDisable(false);
                        summary.setText(Ui.userMessage(err));
                    });
        });

        VBox content = new VBox(10, hint, new HBox(10, choose, template, fileLabel), summary,
                table, problemsLabel, problems);
        content.setPadding(new Insets(4));
        pane.setContent(content);
        dialog.showAndWait();
    }

    private static TableColumn<Candidate, String> column(String title, double width,
                                                         Function<Candidate, String> text) {
        TableColumn<Candidate, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        return col;
    }

    // ===============================================================
    // Export
    // ===============================================================

    public static void showExport(AppContext ctx, Window owner) {
        ImportExportService service = new ImportExportService();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18n.t("export_title"));
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().setAll(Theme.stylesheets());
        pane.setMinWidth(520);

        ComboBox<ExportFormat> format = new ComboBox<>(FXCollections.observableArrayList(ExportFormat.values()));
        format.setValue(ExportFormat.CSV);
        ComboBox<Department> department = new ComboBox<>(Ui.departmentItems(ctx, true));
        department.setConverter(Ui.departmentConverter());
        department.setValue(Ui.ALL_DEPARTMENTS);

        int year = LocalDate.now().getYear();
        DatePicker from = new DatePicker(LocalDate.of(year, 1, 1));
        DatePicker to = new DatePicker(LocalDate.of(year, 12, 31));
        TextField title = new TextField("MBSTU Academic Calendar " + year);
        for (Region r : new Region[] {format, department, from, to, title}) {
            r.setMaxWidth(Double.MAX_VALUE);
        }

        Label note = new Label(I18n.t("export_note"));
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(new Label(I18n.t("export_format")), 0, 0);
        grid.add(format, 1, 0);
        grid.add(new Label(I18n.t("f_department")), 0, 1);
        grid.add(department, 1, 1);
        grid.add(new Label(I18n.t("suggest_from")), 0, 2);
        grid.add(from, 1, 2);
        grid.add(new Label(I18n.t("suggest_to")), 0, 3);
        grid.add(to, 1, 3);
        grid.add(new Label(I18n.t("f_title")), 0, 4);
        grid.add(title, 1, 4);
        GridPane.setHgrow(format, Priority.ALWAYS);

        pane.setContent(new VBox(12, grid, note, error));

        ButtonType exportType = new ButtonType(I18n.t("export_btn"), ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(exportType, new ButtonType(I18n.t("cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        Button exportButton = (Button) pane.lookupButton(exportType);

        exportButton.addEventFilter(ActionEvent.ACTION, ev -> {
            ev.consume();
            error.setText("");
            ExportFormat chosen = format.getValue();

            FileChooser chooser = new FileChooser();
            chooser.setInitialFileName("mbstu-calendar." + chosen.extension());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    chosen.toString(), "*." + chosen.extension()));
            File file = chooser.showSaveDialog(pane.getScene().getWindow());
            if (file == null) {
                return;
            }

            Integer departmentId = Ui.deptIdOrNull(department.getValue());
            LocalDate fromDate = from.getValue();
            LocalDate toDate = to.getValue();
            String heading = title.getText();
            exportButton.setDisable(true);

            Async.run(() -> service.export(ctx.getUser(), chosen, file.toPath(), departmentId,
                            fromDate, toDate, heading),
                    count -> {
                        dialog.close();
                        Ui.info(I18n.t("export_title"),
                                I18n.f("export_done", I18n.number(count), file.getAbsolutePath()));
                    },
                    err -> {
                        exportButton.setDisable(false);
                        error.setText(Ui.userMessage(err));
                    });
        });
        dialog.showAndWait();
    }
}
