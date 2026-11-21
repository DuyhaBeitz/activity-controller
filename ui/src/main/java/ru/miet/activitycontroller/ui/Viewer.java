package ru.miet.activitycontroller.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.StackPane;
import javafx.application.Platform;
import javafx.stage.Stage;

public class Viewer extends Application
{
    @Override 
    public void start(Stage stage) throws Exception
    {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        FXMLLoader mapLoader = new FXMLLoader(getClass().getResource("/ru/miet/activitycontroller/scene.fxml"));
        Parent mapRoot = mapLoader.load();
        MapController mapController = mapLoader.getController();

        FXMLLoader listLoader = new FXMLLoader(getClass().getResource("/ru/miet/activitycontroller/allservers_scene.fxml"));
        Parent listRoot = listLoader.load();
        AllServersController listController = listLoader.getController();

        listController.setDependencies(mapController);
        mapController.setAllServersController(listController);
        

        StackPane rootStack = new StackPane();
        rootStack.getChildren().addAll(mapRoot, listRoot);
        listRoot.setVisible(false);

        Scene scene = new Scene(rootStack);
        ThemeManager.applyToScene(scene);
        stage.setTitle("Мониторинг работоспособности сервисов");
        stage.setScene(scene);

        javafx.stage.Screen screen = javafx.stage.Screen.getPrimary();
        javafx.geometry.Rectangle2D bounds = screen.getVisualBounds();

        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());

        stage.setOnCloseRequest(windowEvent -> {
            System.out.println("Закрытие приложения. Очищаем ресурсы...");

            if (mapController != null)
            {
                mapController.shutdownMonitoring();
            }

            javafx.application.Platform.exit();
            System.exit(0);
        });

        stage.show();
    }

    public static void showErrorAlert(String title, String content) 
    {
        if (Platform.isFxApplicationThread())
        {
            executeAlert(title, content);
        }
        else
        {
            Platform.runLater(() -> executeAlert(title, content));
        }
    }

    private static void executeAlert(String title, String content)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}