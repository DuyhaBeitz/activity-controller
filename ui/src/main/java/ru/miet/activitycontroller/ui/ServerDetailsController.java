package ru.miet.activitycontroller.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import ru.miet.activitycontroller.core.Database;
import ru.miet.activitycontroller.core.Pinger;
import ru.miet.activitycontroller.core.Database.ServerResult;
import ru.miet.activitycontroller.core.ServerStatus;
import javafx.scene.layout.Region;
import javafx.concurrent.Task;

public class ServerDetailsController
{
    @FXML private VBox rootLayout;
    @FXML private Label titleLabel;
    @FXML private Label statusLabel;
    @FXML private HBox switchBox;
    @FXML private Label switchLabel;
    @FXML private ToggleButton statusSwitch;
    @FXML private VBox notesBox;
    @FXML private TextArea notesArea;
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
        diagnosticButton.setOnAction(e -> onDiagnosticClick());
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

            if (server.notes() != null && !server.notes().trim().isEmpty())
            {
                notesBox.setVisible(true);
                notesBox.setManaged(true);
                notesArea.setText(server.notes());
            }
            else
            {
                notesBox.setVisible(false);
                notesBox.setManaged(false);
            }

            double width = LayoutScale.scaled(420.0);
            double height = LayoutScale.scaled(540.0);

            stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Информация о сервисе");
            stage.setResizable(false);
            Scene scene = new Scene(root, width, height);
            ThemeManager.applyToScene(scene);
            stage.setScene(scene);
            stage.showAndWait();
        }
        catch(Exception e)
        {
            Viewer.showErrorAlert("Ошибка", "Не удалось открыть информацию о сервисе:\n" + e.getMessage());
        }
    }

    public void update(ServerResult fresh)
    {
        if (fresh.status() != this.server.status())
        {
            this.server = fresh;
            statusLabel.setText("Текущий статус: " + statusText(fresh.status()));

            if (statusSwitch != null && statusSwitch.isVisible())
            {
                statusSwitch.setSelected(fresh.status() == ServerStatus.CONTROL);
                applySwitchStyle(fresh.status() == ServerStatus.CONTROL ? ServerStatus.CONTROL : ServerStatus.ERROR);
            }

        }
    }
        
    public void close()
    {
        if (stage != null)
        {
            stage.close();
        } 
    }

    private void onSwitchToggled()
    {
        ServerStatus target = statusSwitch.isSelected() ? ServerStatus.CONTROL : ServerStatus.ERROR;

        try
        {
            database.updateServerStatus(server.id(), target);
            this.server = new ServerResult(server.id(), server.name(), server.latitude(), server.longitude(), target, server.address(), server.notes());
            statusLabel.setText("Текущий статус: " + statusText(server.status()));
            applySwitchStyle(target);
            mapController.applyStatusToUi(server.id(), target);
        }
        catch(Exception e)
        {
            Viewer.showErrorAlert("Ошибка изменения статуса", e.getMessage());
            statusSwitch.setSelected(server.status() == ServerStatus.CONTROL);
            statusLabel.setText("Текущий статус: " + statusText(server.status()));
            applySwitchStyle(server.status()); 
        }
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
        if (status == ServerStatus.CONTROL)
        {
            statusSwitch.setText("ВКЛ");
            statusSwitch.getStyleClass().setAll("toggle-button", "switch-on");
        }
        else
        {
            statusSwitch.setText("ВЫКЛ");
            statusSwitch.getStyleClass().setAll("toggle-button", "switch-off");
        }
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

    private void onDiagnosticClick()
    {
        diagnosticButton.setDisable(true);
        diagnosticButton.setText("Диагностика...");

        Task<Pinger.PingResult> task = new Task<>()
        {
            @Override
            protected Pinger.PingResult call()
            {
                return Pinger.runPing(server.address());
            }
        };

        task.setOnSucceeded(e -> {
            diagnosticButton.setDisable(false);
            diagnosticButton.setText("Провести диагностику");

            Pinger.PingResult result = task.getValue();
            
            if (result != null)
            {
                showPingResultWindow(result.toString());
            }
            else
            {
                Viewer.showErrorAlert("Ошибка", "Не удается провести диагностику (нет ответа)");
            }
        });

        task.setOnFailed(e -> {
            diagnosticButton.setDisable(false);
            diagnosticButton.setText("Провести диагностику");
            Viewer.showErrorAlert("Ошибка", "Ошибка при выполнении диагностики");
        });

        // 5. Запускаем поток
        new Thread(task).start();
    }

    private void showPingResultWindow(String pingText)
    {
        Stage diagnosticStage = new Stage();
        diagnosticStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        diagnosticStage.setTitle("Результат пинга");
        diagnosticStage.setResizable(false);

        double targetWidth = LayoutScale.scaled(420.0);
        double targetHeight = LayoutScale.scaled(540.0);

        VBox layout = new VBox(LayoutScale.scaled(15));
        layout.getStyleClass().add("card-panel");
        layout.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        Label pingLabel = new Label(pingText);
        pingLabel.getStyleClass().add("ping-console");
        pingLabel.setWrapText(true);
        pingLabel.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button goBackButton = new Button("Ок");
        goBackButton.getStyleClass().add("btn-secondary");
        goBackButton.setMaxWidth(Double.MAX_VALUE);
        goBackButton.setOnAction(e -> diagnosticStage.close());

        layout.getChildren().addAll(pingLabel, spacer, goBackButton);
        Scene scene = new Scene(layout, targetWidth, targetHeight);
        ThemeManager.applyToScene(scene);
        diagnosticStage.setScene(scene);
        diagnosticStage.showAndWait();
    }
}
