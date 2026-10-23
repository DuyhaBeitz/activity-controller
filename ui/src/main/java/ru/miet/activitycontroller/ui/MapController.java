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
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.sql.SQLException;
import java.util.List;

public class MapController
{
    @FXML
    private WebView mapWebView;

    private WebEngine webEngine;

    @FXML
    private VBox serversContainer;

    private Database database;

    private AddServiceController activeDialogController;

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

                loadServersFromDatabase();

                startAutoMonitoring();
            }
        });

        // Слушатель размера для карты (ширина)
        mapWebView.widthProperty().addListener((obs, oldW, newW) -> {
            if (webEngine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED)
            {
                javafx.application.Platform.runLater(() -> {
                    webEngine.executeScript("if(typeof map !== 'undefined') { map.invalidateSize(); }");
                });
            }
        });

        // Слушатель размера для карты (высота)
        mapWebView.heightProperty().addListener((obs, oldH, newH) -> {
            if (webEngine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED)
            {
                javafx.application.Platform.runLater(() -> {
                    webEngine.executeScript("if(typeof map !== 'undefined') { map.invalidateSize(); }");
                });
            }
        });

        if (mapWebView != null)
        {
            javafx.application.Platform.runLater(() -> {
                if (serversContainer != null && serversContainer.getParent() != null)
                {
                    VBox sidePanel = (VBox) serversContainer.getParent();

                    javafx.beans.binding.DoubleBinding responsiveScale = javafx.beans.binding.Bindings.createDoubleBinding(() -> {
                        double width = mapWebView.getWidth();
                        double height = mapWebView.getHeight();
                        if (width <= 0 || height <= 0) return 1.0;
                        
                        double vmin = Math.min(width, height);
                        double scale = vmin / 650.0; 
                        
                        return Math.max(1.15, Math.min(scale, 2.0));
                    },
                    mapWebView.widthProperty(),
                    mapWebView.heightProperty());

                    sidePanel.scaleXProperty().bind(responsiveScale);
                    sidePanel.scaleYProperty().bind(responsiveScale);
                }
            });
        }
    }

    private double getResponsiveSizeJava(double targetVmin, double minPixels)
    {
        if (mapWebView == null) return minPixels;
        
        double width = mapWebView.getWidth();
        double height = mapWebView.getHeight();
        
        if (width <= 0 || height <= 0)
        {
            return minPixels;
        }
        
        double vmin = Math.min(width, height);
        return Math.max(minPixels, Math.round((targetVmin / 100.0) * vmin));
    }

    private void loadServersFromDatabase()
    {
        if (database == null) return;

        javafx.application.Platform.runLater(() -> serversContainer.getChildren().clear());

        try
        {
            List<Database.ServerResult> servers = database.getServers();
            System.out.println("Загружено серверов из БД: " + servers.size());

            for (Database.ServerResult server : servers)
            {
                String jsCommand = String.format(java.util.Locale.US,
                    "addServiceMarker(%d, %.6f, %.6f, '%s', '%s', '%s')",
                    server.id(), server.latitude(), server.longitude(), server.name().replace("'", "\\'"), server.status().name(), server.address()
                );

                javafx.application.Platform.runLater(() -> {
                    webEngine.executeScript(jsCommand);
                });

                javafx.application.Platform.runLater(() -> addServerToSidePanel(server));
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

            double dynamicWidth = getResponsiveSizeJava(25.0, 400.0);
            double dynamicHeight = getResponsiveSizeJava(55.0, 600.0); 

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
            id, lat, lng, name.replace("'", "\\'"), status, address);

            webEngine.executeScript(jsCommand);
        }

        javafx.application.Platform.runLater(() -> {
            try
            {
                ServerResult server = new ServerResult(id, name, lat, lng, ServerStatus.valueOf(status), address);
                addServerToSidePanel(server);
            }
            catch(Exception e)
            {
                Viewer.showErrorAlert("Ошибка", "Критическая ошибка при добавлении сервиса, попробуйте снова");
            }
        });
    }

    private void addServerToSidePanel(Database.ServerResult server)
    {
        HBox serverRow = new HBox(8);
        serverRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        serverRow.setStyle(String.format("-fx-padding: 8; -fx-background-color: #e3e3e3; -fx-background-radius: 4;"));
        serverRow.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.shape.Circle statusIndicator = new javafx.scene.shape.Circle(6);
        String color;
        switch (server.status())
        {
            case OK -> color = "#2ecc71";
            case WARNING -> color = "#f1c40f";
            case ERROR -> color = "#e74c3c";
            case CONTROL -> color = "#e67e22";
            default -> color = "#95a5a6";
        }
        statusIndicator.setStyle("-fx-fill: " + color + "; -fx-stroke: white; -fx-stroke-width: 1;");

        Label nameLabel = new Label(server.name());
        nameLabel.setStyle(String.format("-fx-text-fill: #1c1c1c; -fx-font-size: 14px; -fx-font-weight: bold;"));
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Button detailsButton = new Button("Подробнее");
        detailsButton.setStyle(String.format("-fx-background-color: #3652a1; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 12px;"));
        detailsButton.setOnAction(e -> openServerDetailsModal(server, serverRow));

        serverRow.getChildren().addAll(statusIndicator, nameLabel, detailsButton);
        serversContainer.getChildren().add(serverRow);
    }

    private String setStatusText(String status)
    {
        String statusText = "";
        switch (status)
        {
            case "OK":
                statusText = "Все работает исправно";
                break;
            case "ERROR":
                statusText = "Сервис работает некорректно. Выполните диагностику и обратитесь к системному администратору";
                break;
            case "CONTROL":
                statusText = "У сервиса замечены проблемы. Уже ведутся работы по их исправлению";
                break;
            case "WARNING":
                statusText = "Время ответа сервиса растет. Рекомендуется провести диагностику и следить за его дальнейшей работой";
                break;
            default:
                Viewer.showErrorAlert("Ошибка", "Получен некорректный статус");
                break;
        }

        return statusText;
    }

    private void openServerDetailsModal(ServerResult server, HBox serverRowInPanel)
    {
        Stage infoStage = new Stage();
        infoStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        infoStage.setTitle("Информация о сервисе");
        infoStage.setResizable(false);

        double modalWidth = getResponsiveSizeJava(40.0, 600);
        double modalHeight = getResponsiveSizeJava(25.0, 480); 
        double padding = getResponsiveSizeJava(2.0, 15);
        double titleSize = getResponsiveSizeJava(2.0, 24);
        double textSize = getResponsiveSizeJava(1.5, 16);

        VBox layout = new VBox(getResponsiveSizeJava(1.5, 10));
        layout.setStyle(String.format("-fx-padding: %.0f; -fx-background-color: white;", padding));
        layout.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        Label titleLabel = new Label("Сервис: " + server.name());
        titleLabel.setStyle(String.format("-fx-text-fill: #1c1c1c; -fx-font-size: %.0fpx; -fx-font-weight: bold;", titleSize));
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        String statusText = setStatusText(server.status().name());

        Label statusLabel = new Label("Текущий статус: " + statusText);
        statusLabel.setStyle(String.format("-fx-text-fill: #1c1c1c; -fx-font-size: %.0fpx;", textSize));
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        // Label adminLabel = new Label();
        // можно будет добавить несколько контактов (почта, соцсети)

        layout.getChildren().addAll(titleLabel, statusLabel);

        // Вспомогательный контейнер для удобного перезаписывания
        final ServerResult[] currentServerHolder = { server };

        if (currentServerHolder[0].status() == ServerStatus.ERROR ||
            currentServerHolder[0].status() == ServerStatus.CONTROL)
        {
            HBox switchBox = new HBox(10);
            switchBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            switchBox.setMaxWidth(Double.MAX_VALUE);

            Label switchLabel = new Label("Включите, если админ взял сервис на контроль:");
            switchLabel.setStyle(String.format("-fx-text-fill: white; -fx-font-size: %.0fpx;", textSize));
            switchLabel.setWrapText(true);

            HBox.setHgrow(switchLabel, javafx.scene.layout.Priority.ALWAYS);
            switchLabel.setMaxWidth(Double.MAX_VALUE);

            ToggleButton statusSwitch = new ToggleButton("ВЫКЛ");
            statusSwitch.setStyle(String.format("-fx-background-color: #e74c3c; -fx-text-fill: white;-fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: %.0fpx;", textSize));

            if (currentServerHolder[0].status() == ServerStatus.CONTROL)
            {
                statusSwitch.setSelected(true);
                statusSwitch.setText("ВКЛ");
                statusSwitch.setStyle(String.format("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: %.0fpx;", textSize));
            }

            statusSwitch.setOnAction(e -> {
                try
                {
                    ServerStatus targetStatus = statusSwitch.isSelected() ?
                                ServerStatus.CONTROL : ServerStatus.ERROR;

                    // Обновляем статус в базе данных

                    currentServerHolder[0] = new Database.ServerResult(
                        currentServerHolder[0].id(), 
                        currentServerHolder[0].name(), 
                        currentServerHolder[0].latitude(), 
                        currentServerHolder[0].longitude(), 
                        targetStatus,
                        currentServerHolder[0].address()
                    );

                    statusLabel.setText("Текущий статус: " + setStatusText(targetStatus.name()));
                    if (statusSwitch.isSelected()) {
                        statusSwitch.setText("ВКЛ");
                        statusSwitch.setStyle(String.format("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: %.0fpx;", textSize));
                    } else {
                        statusSwitch.setText("ВЫКЛ");
                        statusSwitch.setStyle(String.format("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: %.0fpx;", textSize));
                    }

                    if (serverRowInPanel != null && !serverRowInPanel.getChildren().isEmpty() && serverRowInPanel.getChildren().get(0) instanceof javafx.scene.shape.Circle)
                    {
                        javafx.scene.shape.Circle indicator = (javafx.scene.shape.Circle) serverRowInPanel.getChildren().get(0);
                        String indColor = (targetStatus == ServerStatus.CONTROL) ? "#e67e22" : "#e74c3c";
                        indicator.setStyle("-fx-fill: " + indColor + "; -fx-stroke: white; -fx-stroke-width: 1;");
                    }

                    javafx.application.Platform.runLater(() -> {
                        webEngine.executeScript(String.format("updateServiceMarkerStatus(%d, '%s')", currentServerHolder[0].id(), targetStatus.name()));
                    });
                }
                catch (Exception ex)
                {
                    Viewer.showErrorAlert("Ошибка изменения статуса", ex.getMessage());
                    statusSwitch.setSelected(!statusSwitch.isSelected());
                }
            });

            switchBox.getChildren().addAll(switchLabel, statusSwitch);
            layout.getChildren().add(switchBox);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        VBox buttonsBox = new VBox(getResponsiveSizeJava(1.2, 15));
        buttonsBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonsBox.setMaxWidth(Double.MAX_VALUE);

        String baseBtnStyle = "-fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6; -fx-font-weight: bold; -fx-font-size: %.0fpx;";
        String blueStyle = String.format("-fx-background-color: #3652a1; " + baseBtnStyle, textSize);
        String darkBlueStyle = String.format("-fx-background-color: #232853; " + baseBtnStyle, textSize);
        String redStyle = String.format("-fx-background-color: #e74c3c; " + baseBtnStyle, textSize);

        Button diagnosticButton = new Button("Провести диагностику");
        diagnosticButton.setStyle(blueStyle);
        diagnosticButton.setMaxWidth(Double.MAX_VALUE);
        // diagnosticButton.setOnAction();  получение подробной инфы о сервисе

        HBox hButtonsBox = new HBox(getResponsiveSizeJava(1.1, 20));
        hButtonsBox.setMaxWidth(Double.MAX_VALUE);
        hButtonsBox.setAlignment(javafx.geometry.Pos.CENTER);

        Button closeButton = new Button("Закрыть");
        closeButton.setMaxWidth(Double.MAX_VALUE);
        closeButton.setStyle(darkBlueStyle);
        closeButton.setOnAction(e -> infoStage.close());

        Button deleteButton = new Button("Удалить");
        deleteButton.setStyle(redStyle);
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        // closeButton.setOnAction(e -> {
        //     try
        //     {
        //         // метод удаления сервера из базы данных

        //         serversContainer.getChildren().remove(serverRowInPanel);
        //         javafx.application.Platform.runLater(() -> {
        //             webEngine.executeScript(String.format("if(markersMap[%d]) { markerClusterGroup.removeLayer(markersMap[%d]); delete markersMap[%d]; }", currentServerHolder[0].id(), currentServerHolder[0].id(), currentServerHolder[0].id()));
        //         });
        //         infoStage.close();
        //     }
        //     catch (Exception ex)
        //     {
        //         Viewer.showErrorAlert("Ошибка удаления", ex.getMessage());
        //     }
        // });

        hButtonsBox.getChildren().addAll(deleteButton, closeButton);
        HBox.setHgrow(deleteButton, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(closeButton, javafx.scene.layout.Priority.ALWAYS);

        buttonsBox.getChildren().addAll(diagnosticButton, hButtonsBox);
        layout.getChildren().addAll(spacer, buttonsBox);

        infoStage.setScene(new Scene(layout, modalWidth, modalHeight));
        infoStage.showAndWait();
    }

    public void startAutoMonitoring()
    {
        if (monitoringScheduler != null && !monitoringScheduler.isShutdown())
        {
            monitoringScheduler.shutdown();
        }

        monitoringScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

        monitoringScheduler.scheduleAtFixedRate(() -> {
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

    private void refreshAllUiComponents()
    {
        if (database == null) return;

        try
        {
            List<ServerResult> updatedServers = database.getServers();

            if (serversContainer != null)
            {
                serversContainer.getChildren().clear();
            }

            for (ServerResult server : updatedServers)
            {
                webEngine.executeScript(String.format(
                "if (typeof updateServiceMarkerStatus === 'function') { updateServiceMarkerStatus(%d, '%s'); }", 
                server.id(), server.status().name()
                ));

                addServerToSidePanel(server);
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
}
