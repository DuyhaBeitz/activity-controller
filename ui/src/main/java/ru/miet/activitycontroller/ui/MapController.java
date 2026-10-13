package ru.miet.activitycontroller.ui;

import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import ru.miet.activitycontroller.core.Database;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

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
            }
        });

        // Слушатель размера (ширина)
        mapWebView.widthProperty().addListener((obs, oldW, newW) -> {
            if (webEngine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED)
            {
                javafx.application.Platform.runLater(() -> {
                    webEngine.executeScript("if(typeof map !== 'undefined') { map.invalidateSize(); }");
                });
            }
        });

        // Слушатель размера (высота)
        mapWebView.heightProperty().addListener((obs, oldH, newH) -> {
            if (webEngine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED)
            {
                javafx.application.Platform.runLater(() -> {
                    webEngine.executeScript("if(typeof map !== 'undefined') { map.invalidateSize(); }");
                });
            }
        });
    }

    private double getResponsiveSizeJava(double targetVmin, double minPixels)
    {
        if (mapWebView == null) return minPixels;
        
        double width = mapWebView.getWidth();
        double height = mapWebView.getHeight();
        
        if (width <= 0 || height <= 0) {
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
                    "addServiceMarker(%d, %.6f, %.6f, '%s', '%s')",
                    server.id(), server.latitude(), server.longitude(), server.name().replace("'", "\\'"), server.status().name()
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

    public void addNewMarkerDirectly(int id, float lat, float lng, String name, String status)
    {
        if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
        {
            String jsCommand = String.format(java.util.Locale.US,
            "addServiceMarker(%d, %.6f, %.6f, '%s', '%s')",
            id, lat, lng, name.replace("'", "\\'"), status);

            webEngine.executeScript(jsCommand);
        }
    }

    private void addServerToSidePanel(Database.ServerResult server)
    {
        double rowPadding = getResponsiveSizeJava(0.8, 6);
        double circleRadius = getResponsiveSizeJava(0.7, 5);
        double fontSize = getResponsiveSizeJava(1.6, 12);
        double btnFontSize = getResponsiveSizeJava(1.4, 11);

        HBox serverRow = new HBox(8);
        serverRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        serverRow.setStyle(String.format("-fx-padding: %.0f; -fx-background-color: #e3e3e3; -fx-background-radius: 4;", rowPadding));
        serverRow.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.shape.Circle statusIndicator = new javafx.scene.shape.Circle(circleRadius);
        String color;
        switch (server.status())
        {
            case OK -> color = "#2ecc71";
            case WARNING -> color = "#f1c40f";
            case ERROR, CONTROL -> color = "#e74c3c";
            default -> color = "#95a5a6";
        }
        statusIndicator.setStyle("-fx-fill: " + color + "; -fx-stroke: white; -fx-stroke-width: 1;");

        Label nameLabel = new Label(server.name());
        nameLabel.setStyle(String.format("-fx-text-fill: #1c1c1c; -fx-font-size: %.0fpx; -fx-font-weight: bold;", fontSize));
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        Button detailsButton = new Button("Подробнее");
        detailsButton.setStyle(String.format("-fx-background-color: #3652a1; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: %.0fpx;", btnFontSize));

        serverRow.getChildren().addAll(statusIndicator, nameLabel, detailsButton);
        serversContainer.getChildren().add(serverRow);
    }

    public void updateServiceStatusInUI(int serviceId, String status)
    {
        if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
        {
            javafx.application.Platform.runLater(() -> {
                webEngine.executeScript(String.format("updateServiceStatus(%d, '%s')", serviceId, status));
            });
        }
    }
}
