package ru.miet.activitycontroller.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ru.miet.activitycontroller.core.Database;
import ru.miet.activitycontroller.core.Database.ServerResult;
import ru.miet.activitycontroller.core.ServerStatus;

public class ServerDetailsController
{
    @FXML private VBox rootLayout;
    @FXML private Label titleLabel;
    @FXML private Label statusLabel;
    @FXML private HBox switchBox;
    @FXML private Label switchLabel;
    @FXML private ToggleButton statusSwitch;
    @FXML private VBox buttonsBox;
    @FXML private Button diagnosticButton;
    @FXML private Button deleteButton;
    @FXML private Button closeButton;

    private ServerResult server;
    private Database database;
    private MapController mapController;
    private Stage stage;

    public void setDependencies(ServerResult server, Database database, MapController mapController)
    {
        this.server = server;
        this.database = database;
        this.mapController = mapController;
    }

    public int getServerId()
    {
        return server.id();
    }

    @FXML
    public void initialize()
    {
        closeButton.setOnAction(e -> close());
        deleteButton.setOnAction(e -> onDelete());
        statusSwitch.setOnAction(e -> onSwitchToggled());
    }

    public void show()
    {
        try
        {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/ru/miet/activitycontroller/serverdetails_scene.fxml"));

            loader.setController(this);
            Parent root = loader.load();

            titleLabel.setText("Сервис: " + server.name());
            statusLabel.setText("Текущий статус: " + statusText(server.status()));

            boolean showSwitch = server.status() == ServerStatus.ERROR || server.status() == ServerStatus.CONTROL;
            switchBox.setVisible(showSwitch);
            switchBox.setManaged(showSwitch);

            if (showSwitch)
            {
                statusSwitch.setSelected(server.status() == ServerStatus.CONTROL);
                applySwitchStyle(server.status() == ServerStatus.CONTROL ? ServerStatus.CONTROL : ServerStatus.ERROR);
            }

            applyLayoutScale();

            double width = LayoutScale.scaled(420.0);
            double height = LayoutScale.scaled(540.0);

            stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Информация о сервисе");
            stage.setResizable(false);
            stage.setScene(new Scene(root, width, height));
            stage.showAndWait();
        }
        catch(Exception e)
        {
            Viewer.showErrorAlert("Ошибка", "Не удалось открыть информацию о сервисе:\n" + e.getMessage());
        }
    }
        
    private void close()
    {
        if (stage != null)
        {
            stage.close();
        } 
    }

    private void onSwitchToggled()
    {
        ServerStatus target = statusSwitch.isSelected() ? ServerStatus.CONTROL : ServerStatus.ERROR;
        statusLabel.setText("Текущий статус: " + statusText(server.status()));
        applySwitchStyle(target);
    }

    private void onDelete()
    {
        try
        {
            database.removeServer(server.id());
            mapController.onServerDeleted(server.id());
            close();
        }
        catch(Exception e)
        {
            Viewer.showErrorAlert("Ошибка удаления", e.getMessage());
        }
    }

    private void applySwitchStyle(ServerStatus status)
    {
        double textSize = LayoutScale.scaled(16.0);
        if (status == ServerStatus.CONTROL)
        {
            statusSwitch.setText("ВКЛ");
            statusSwitch.setStyle(String.format(
                "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: %.0fpx;",
                textSize));
        }
        else
        {
            statusSwitch.setText("ВЫКЛ");
            statusSwitch.setStyle(String.format(
                "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: %.0fpx;",
                textSize));
        }
    }

    private void applyLayoutScale()
    {
        double textSize = LayoutScale.scaled(16.0);
        double titleSize = LayoutScale.scaled(24.0);
        double padding = LayoutScale.scaled(15.0);
        double spacing = LayoutScale.scaled(15.0);
        double buttonFontSize = LayoutScale.scaled(16.0);

        rootLayout.setSpacing(spacing);
        rootLayout.setPadding(new Insets(padding));

        titleLabel.setStyle(String.format(
            "-fx-text-fill: #1c1c1c; -fx-font-size: %.0fpx; -fx-font-weight: bold;", titleSize));
        statusLabel.setStyle(String.format(
            "-fx-text-fill: #1c1c1c; -fx-font-size: %.0fpx;", textSize));

        switchLabel.setStyle(String.format(
            "-fx-text-fill: #1c1c1c; -fx-font-size: %.0fpx;", textSize));
        statusSwitch.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        switchBox.setSpacing(spacing);

        String baseBtnStyle = "-fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: %.0fpx;";
        diagnosticButton.setStyle(String.format("-fx-background-color: #3652a1; " + baseBtnStyle, buttonFontSize));
        closeButton.setStyle(String.format("-fx-background-color: #232853; " + baseBtnStyle, buttonFontSize));
        deleteButton.setStyle(String.format("-fx-background-color: #e74c3c; " + baseBtnStyle, buttonFontSize));

        buttonsBox.setSpacing(spacing);
    }

    private static String statusText(ServerStatus status)
    {
        return switch (status)
        {
            case OK -> "Все работает исправно";
            case ERROR -> "Сервис работает некорректно. Выполните диагностику и обратитесь к системному администратору";
            case CONTROL -> "У сервиса замечены проблемы. Уже ведутся работы по их исправлению";
            case WARNING -> "Время ответа сервиса растет. Рекомендуется провести диагностику и следить за его дальнейшей работой";
        };
    }
}
