package ru.miet.activitycontroller.ui;

// import javafx.application.Application;
// import javafx.fxml.FXMLLoader;
// import javafx.scene.Parent;
// import javafx.scene.Scene;
// import javafx.stage.Stage;
// import java.io.IOException;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class MapController
{
    @FXML
    private WebView mapWebView;

    private WebEngine webEngine;

    @FXML
    public void initialize()
    {
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
                createInitialMarkers();
                // runDelayedUiTest();
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

    public void openDiagnostic(int serviceId)
    {
        // Информация о выбранном сервисе
    }

    private void createInitialMarkers()
    {
        // вспомогательный метод
        webEngine.executeScript("addServiceMarker(1, 55.7558, 37.6173, 'Москва: Сервер ЦОД', 'OK')");
        webEngine.executeScript("addServiceMarker(2, 40.7128, -74.0060, 'Нью-Йорк: Шлюз авторизации', 'ERROR')");
        webEngine.executeScript("addServiceMarker(3, 40.7300, -73.9950, 'Нью-Йорк: Резервный коммутатор', 'WARNING')");
        webEngine.executeScript("addServiceMarker(4, 40.6300, -73.9050, 'Нью-Йорк: Гей-клуб', 'CONTROL')");
    }

    // private void runDelayedUiTest()
    // {
    //     System.out.println("[Тест UI] Карта загружена. Таймер на 10 секунд запущен...");

    //     // Создаем паузу на 10 секунд
    //     PauseTransition pause = new PauseTransition(Duration.seconds(10));
        
    //     // Какое действие выполнить, когда время выйдет
    //     pause.setOnFinished(event -> {
    //         System.out.println("[Тест UI] 10 секунд прошло! Меняем статусы двух сервисов...");
            
    //         // 1. Меняем статус Сервиса 2 (Нью-Йорк) с ERROR на OK
    //         updateServiceStatusInUI(2, "WARNING");
            
    //         // 2. Меняем статус Сервиса 4 (Нью-Йорк) с WARNING на ERROR
    //         updateServiceStatusInUI(4, "OK");
    //     });
        
    //     // Запускаем таймер
    //     pause.play();
    // }
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
