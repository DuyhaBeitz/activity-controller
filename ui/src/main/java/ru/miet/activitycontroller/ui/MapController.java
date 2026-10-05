package ru.miet.activitycontroller.ui;

import javafx.stage.Modality;
// import javafx.application.Application;
// import javafx.fxml.FXMLLoader;
// import javafx.scene.Parent;
// import javafx.scene.Scene;
import javafx.stage.Stage;
// import java.io.IOException;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import ru.miet.activitycontroller.core.Database;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.util.Duration;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.sql.SQLException;
import java.util.List;

public class MapController
{
    @FXML
    private WebView mapWebView;

    private WebEngine webEngine;

    private Database database;

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

    private void loadServersFromDatabase()
    {
        if (database == null) return;

        try
        {
            List<Database.ServerResult> servers = database.getServers();
            System.out.println("Загружено серверов из БД: " + servers.size());

            for (Database.ServerResult server : servers)
            {
                String jsCommand = String.format(java.util.Locale.US,
                    "addServiceMarker(%d, %.6f, %.6f, '%s', 'OK')",
                    server.id(), server.latitude(), server.longitude(), server.name().replace("'", "\\'")
                );
                webEngine.executeScript(jsCommand);
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
            controller.setDependencies(database, this);

            double mainWindowWidth = mapWebView.getScene().getWidth();
            double mainWindowHeight = mapWebView.getScene().getHeight();

            double dynamicWidth = mainWindowWidth * 0.25;
            double dynamicHeight = mainWindowHeight * 0.50;

            dynamicWidth = Math.max(320.0, dynamicWidth);
            dynamicHeight = Math.max(400.0, dynamicHeight);

            Stage modalStage = new Stage();
            modalStage.initModality(Modality.APPLICATION_MODAL);
            modalStage.setTitle("Добавление нового сервиса");
            modalStage.setResizable(false);
            modalStage.setScene(new Scene(root, dynamicWidth, dynamicHeight));
            modalStage.showAndWait();
        }
        catch (Exception e)
        {
        System.err.println("Не удалось открыть модальное окно добавления: " + e.getMessage());
        e.printStackTrace();
    }
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

    public void openDiagnostic(int serviceId)
    {
        // Информация о выбранном сервисе
    }

    public void updateServiceStatusInUI(int serviceId, String status)
    {
        if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED)
        {
            Platform.runLater(() -> {
                // Вызываем простую функцию, которую мы добавили в HTML
                webEngine.executeScript(String.format("updateServiceStatus(%d, '%s')", serviceId, status));
            });
        }
    }
}
