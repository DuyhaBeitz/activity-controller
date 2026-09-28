package ru.miet.activitycontroller.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Viewer extends Application
{
    @Override 
    public void start(Stage stage) throws Exception
    {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ru/miet/activitycontroller/scene.fxml"));
        Parent root = fxmlLoader.load();
        
        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("Мониторинг работоспособности сервисов");
        stage.setScene(scene);
        stage.show();
    }
}