package ru.miet.activitycontroller.ui;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import ru.miet.activitycontroller.core.Database;
import ru.miet.activitycontroller.core.ServerStatus;
import ru.miet.activitycontroller.core.Service;
import ru.miet.activitycontroller.core.Database.ServerResult;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapController
{
    @FXML
    private WebView mapWebView;

    private WebEngine webEngine;

    @FXML
    private VBox sidePanel;

    @FXML
    private VBox serversContainer;

    @FXML
    private Label mainLabel;

    @FXML
    private Button addServiceButton;

    private Database database;

    private AddServiceController activeDialogController;
    private ServerDetailsController openDetailsController;

    private final Map<Integer, ServerRowView> serverRowsById = new HashMap<>();
    private final Map<Integer, ServerResult> serversById = new HashMap<>();

    private PauseTransition resizeDebounce;

    public WebEngine getWebEngine()
    {
        return this.webEngine;
    }

    private ScheduledExecutorService monitoringScheduler;

    @FXML
    public void initialize()
    {
        try
        {
            database = new Database();
        }
        catch (SQLException e)
        {
            System.err.println("Не удалось подключиться к базе данных: " + e.getMessage());
            Viewer.showErrorAlert("Ошибка подключения", 
                "Не удалось подключиться к базе данных:\n" + e.getMessage() +
                "\n\nПриложение будет закрыто."
            );
            Platform.runLater(() -> {
                Stage stage = (Stage) mapWebView.getScene().getWindow();
                stage.close();
            });
            return;
        }

        webEngine = mapWebView.getEngine();

        java.net.URL htmlUrl = getClass().getResource("/ru/miet/activitycontroller/map/worldmap.html");
        if (htmlUrl != null)
        {
            webEngine.load(htmlUrl.toExternalForm());
        }
        else
        {
            System.err.println("Критическая ошибка: Не удалось найти worldmap.html в ресурсах!");
        }

        // Слушатель загрузки
        webEngine.getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == javafx.concurrent.Worker.State.SUCCEEDED)
            {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("javaApp", this);

                webEngine.executeScript("window.deviceScreenScale = " + LayoutScale.getScreenScale() + ";");
                loadServersFromDatabase();

                startAutoMonitoring();
            }
        });

        resizeDebounce = new PauseTransition(Duration.millis(150));
        resizeDebounce.setOnFinished(e -> {
            if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
            {
                webEngine.executeScript("if(typeof map !== 'undefined') { map.invalidateSize(); }");
            }
        });

        mapWebView.widthProperty().addListener((obs, oldW, newW) -> resizeDebounce.playFromStart());
        mapWebView.heightProperty().addListener((obs, oldH, newH) -> resizeDebounce.playFromStart());

        if (mapWebView != null)
        {
            javafx.application.Platform.runLater(() -> {
                if (sidePanel != null)
                {
                    sidePanel.setMinWidth(LayoutScale.scaled(320.0));
                    sidePanel.setSpacing(LayoutScale.scaled(15.0));
                    sidePanel.setPadding(new Insets(LayoutScale.scaled(20.0)));
                }
                if (serversContainer != null)
                {
                    serversContainer.setSpacing(LayoutScale.scaled(10.0));
                }
                updateMainUiStyles();
            });
        }
    }

    private void loadServersFromDatabase()
    {
        if (database == null)
        {
            // Явно сообщаем в UI, а не молчим
            javafx.application.Platform.runLater(() -> {
                mainLabel.setText("НЕТ ПОДКЛЮЧЕНИЯ К БД");
                Double fontsize = LayoutScale.scaled(18);
                mainLabel.setStyle("-fx-font-size:"+fontsize+"px; -fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            });
            return;
        }

        try
        {
            List<Database.ServerResult> servers = database.getServers();
            System.out.println("Загружено серверов из БД: " + servers.size());

            for (Database.ServerResult server : servers)
            {
                String jsCommand = String.format(java.util.Locale.US,
                    "addServiceMarker(%d, %.6f, %.6f, '%s', '%s', '%s')",
                    server.id(),
                    server.latitude(),
                    server.longitude(),
                    escapeJs(server.name()),
                    escapeJs(server.status().name()),
                    escapeJs(server.address())
                );

                javafx.application.Platform.runLater(() -> {
                    webEngine.executeScript(jsCommand);
                    createServerRow(server);
                    serversById.put(server.id(), server);
                });
            }
        }
        catch (SQLException e)
        {
            System.err.println("Ошибка при загрузке данных из БД на карту: " + e.getMessage());
        }
    }

    @FXML 
    private void onAddServiceButton()
    {
        try
        {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ru/miet/activitycontroller/addservice_scene.fxml"));
            Parent root = loader.load();

            AddServiceController controller = loader.getController();
            this.activeDialogController = controller;
            controller.setDependencies(database, this);

            Stage modalStage = new Stage();
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.setTitle("Добавление нового сервиса");
            modalStage.setResizable(false);

            double dynamicWidth = LayoutScale.scaled(500.0);
            double dynamicHeight = LayoutScale.scaled(620.0);

            modalStage.setScene(new Scene(root, dynamicWidth, dynamicHeight));
            modalStage.showAndWait();
        }
        catch (Exception e)
        {
        System.err.println("Не удалось открыть модальное окно добавления: " + e.getMessage());
        e.printStackTrace();
    }
    }

    public void onCoordinatesSelected(double lat, double lng)
    {
        javafx.application.Platform.runLater(() -> {
            if (activeDialogController != null)
            {
                activeDialogController.populateCoordinates(lat, lng);
            }
        });
    }

    public void addNewMarkerDirectly(int id, float lat, float lng, String name, String status, String address)
    {
        if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
        {
            String jsCommand = String.format(java.util.Locale.US,
            "addServiceMarker(%d, %.6f, %.6f, '%s', '%s', '%s')",
            id,
            lat,
            lng,
            escapeJs(name),
            escapeJs(status),
            escapeJs(address));

            webEngine.executeScript(jsCommand);
        }

        javafx.application.Platform.runLater(() -> {
            try
            {
                ServerResult server = new ServerResult(id, name, lat, lng, ServerStatus.valueOf(status), address);
                createServerRow(server);
                serversById.put(id, server);
            }
            catch(Exception e)
            {
                Viewer.showErrorAlert("Ошибка", "Критическая ошибка при добавлении сервиса, попробуйте снова");
            }
        });
    }

    private void createServerRow(ServerResult server)
    {
        HBox serverRow = new HBox(8);
        serverRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        int padding = (int) LayoutScale.scaled(8);
        serverRow.setStyle(String.format("-fx-padding: " + padding + "; -fx-background-color: #e3e3e3; -fx-background-radius: 4;"));
        serverRow.setMaxWidth(Double.MAX_VALUE);

        int radius = (int) LayoutScale.scaled(6);
        javafx.scene.shape.Circle statusIndicator = new javafx.scene.shape.Circle(radius);
        updateCircleColor(statusIndicator, server.status());

        Label nameLabel = new Label(server.name());
        double labelFontSize = LayoutScale.scaled(14);
        nameLabel.setStyle(String.format("-fx-text-fill: #1c1c1c; -fx-font-size: " + labelFontSize + "; -fx-font-weight: bold;"));
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Button detailsButton = new Button("Подробнее");
        double buttonFontSize = LayoutScale.scaled(12);
        detailsButton.setStyle(String.format("-fx-background-color: #3652a1; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: " + buttonFontSize + ";"));
        detailsButton.setOnAction(e -> {
            openDetailsController = new ServerDetailsController();
            openDetailsController.setDependencies(server, database, this);
            openDetailsController.show();
            openDetailsController = null;
        });

        serverRow.getChildren().addAll(statusIndicator, nameLabel, detailsButton);
        serversContainer.getChildren().add(serverRow);

        ServerRowView view = new ServerRowView(serverRow, statusIndicator, nameLabel, detailsButton, server.status());
        serverRowsById.put(server.id(), view);
    }

    private void updateCircleColor(Circle circle, ServerStatus status)
    {
        String color = switch (status)
        {
            case OK -> "#2ecc71";
            case WARNING -> "#f1c40f";
            case ERROR -> "#e74c3c";
            case CONTROL -> "#e67e22";
            default -> "#95a5a6";
        };
        circle.setStyle("-fx-fill: " + color + "; -fx-stroke: white; -fx-stroke-width: 1;");
    }

    public void openDiagnostic(int id)
    {
        ServerResult server = serversById.get(id);
        if (server == null)
        {
            Viewer.showErrorAlert("Ошибка", "Не удается открыть информацию о сервисе");
            return;
        }
        
        openDetailsController = new ServerDetailsController();
        openDetailsController.setDependencies(server, database, this);
        openDetailsController.show();
        openDetailsController = null;
    }

    public void onServerDeleted(int id)
    {
        if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
        {
            webEngine.executeScript(String.format(
                "if(markersMap[%d]) { markerClusterGroup.removeLayer(markersMap[%d]); delete markersMap[%d]; }",
            id, id, id));
        }
        
        ServerRowView rowView = serverRowsById.remove(id);
        if (rowView != null)
        {
            serversContainer.getChildren().remove(rowView.container);
        }

        serversById.remove(id);

        if (openDetailsController != null && openDetailsController.getServerId() == id)
        {
            openDetailsController.close();
            openDetailsController = null;
        }
    }

    public void startAutoMonitoring()
    {
        if (database == null)
        {
            System.out.println("Мониторинг не запущен. База данных не подключена");
            return;
        }

        if (monitoringScheduler != null && !monitoringScheduler.isShutdown())
        {
            monitoringScheduler.shutdown();
        }

        monitoringScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        monitoringScheduler.scheduleWithFixedDelay(() -> {
            try
            {
                Service.update(this.database);
                Platform.runLater(this::refreshAllUiComponents);
            }
            catch(Exception e)
            {
                System.err.println("Ошибка при фоновом опросе серверов: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void applyStatusToUi(int id, ServerStatus status)
    {
        if (webEngine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED)
        {
            webEngine.executeScript(String.format(
                "if (typeof updateServiceMarkerStatus === 'function') { updateServiceMarkerStatus(%d, '%s'); }", 
                id, status.name()
            ));
        }

        ServerRowView rowView = serverRowsById.get(id);
        if (rowView != null && rowView.currentStatus != status)
        {
            updateCircleColor(rowView.statusIndicator, status);
            rowView.currentStatus = status;
        }
    }

    public void updateStatusInUiWithRunLater(int id, ServerStatus status)
    {
        Platform.runLater(() -> applyStatusToUi(id, status));
    }

    private void updateMainUiStyles()
    {
        double labelFontSize = LayoutScale.scaled(18);
        double buttonFontSize = LayoutScale.scaled(14);

        if (mainLabel != null)
        {
            mainLabel.setStyle("-fx-font-size: " + labelFontSize + "px; -fx-text-fill: #232853; -fx-font-weight: bold;");
        }
        if (addServiceButton != null)
        {
            addServiceButton.setStyle("-fx-background-color: #232853; -fx-text-fill: white; -fx-font-size: " + buttonFontSize + "px; -fx-font-weight: bold; -fx-background-radius: 5px; -fx-cursor: hand;");
        }
        
    }
    private void refreshAllUiComponents()
    {
        if (database == null) return;

        try
        {
            List<ServerResult> updatedServers = database.getServers();
            Map<Integer, ServerResult> freshById = new HashMap();
            for (ServerResult s : updatedServers) freshById.put(s.id(), s);

            serverRowsById.entrySet().removeIf(entry -> {
                int id = entry.getKey();
                if (!freshById.containsKey(id))
                {
                    // Удаляем визуал
                    serversContainer.getChildren().remove(entry.getValue().container);
                    if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
                    {
                        webEngine.executeScript(String.format("if(markersMap[%d]) { markerClusterGroup.removeLayer(markersMap[%d]); delete markersMap[%d]; }", id, id, id));
                    }
                    return true;
                }
                return false;
            });

            for (ServerResult fresh : updatedServers)
            {
                ServerRowView rowView = serverRowsById.get(fresh.id());

                if (rowView == null)
                {
                    // Если в бд появляется новый сервер извне - добавляем на карту
                    createServerRow(fresh);
                    if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
                    {
                        String js = String.format(java.util.Locale.US, "addServiceMarker(%d, %.6f, %.6f, '%s', '%s', '%s')",
                            fresh.id(), fresh.latitude(), fresh.longitude(), escapeJs(fresh.name()), fresh.status().name(), escapeJs(fresh.address()));
                        webEngine.executeScript(js);
                    }
                }
                else if (rowView.currentStatus != fresh.status())
                {
                    applyStatusToUi(fresh.id(), fresh.status());
                }
            }

            serversById.clear();
            serversById.putAll(freshById);

            if (openDetailsController != null)
            {
                ServerResult currentForModal = freshById.get(openDetailsController.getServerId());
                if (currentForModal != null)
                {
                    openDetailsController.update(currentForModal);
                }
                else
                {
                    openDetailsController.close();
                }
            }

            System.out.println("Информация о серверах обновлена!");
        }
        catch(Exception e)
        {
            System.out.println("Ошибка обновления интерфейса");
        }
    }
    
    public void shutdownMonitoring()
    {
        if (resizeDebounce != null)
        {
            resizeDebounce.stop();
        }

        if (monitoringScheduler != null && !monitoringScheduler.isShutdown())
        {
            monitoringScheduler.shutdown();
        }

        try
        {
            if (monitoringScheduler != null)
            {
                if (!monitoringScheduler.awaitTermination(2, TimeUnit.SECONDS))
                {
                    monitoringScheduler.shutdownNow();
                }
            }

            if (database != null)
            {
                database.close();
            }
        }
        catch(Exception e)
        {
            monitoringScheduler.shutdownNow();
        }
    }

    private static String escapeJs(String s)
    {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'",  "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
    }
}
