package ru.miet.activitycontroller.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.application.Platform;
import javafx.stage.Stage;

public class Viewer extends Application
{
    @Override 
    public void start(Stage stage) throws Exception
    {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ru/miet/activitycontroller/scene.fxml"));
        Parent root = fxmlLoader.load();
        
        Scene scene = new Scene(root);
        stage.setTitle("Мониторинг работоспособности сервисов");
        stage.setScene(scene);

        stage.setMaximized(true);

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