package ru.miet.activitycontroller.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;
import ru.miet.activitycontroller.core.AppSettings;

public class ThemeManager
{

    private static final String CSS_PATH = "/ru/miet/activitycontroller/styles/styles.css";
    private static boolean isDarkTheme = AppSettings.isDarkTheme();
    private static MapController mapController;


    public static void registerMapController(MapController controller)
    {
        mapController = controller;
    }

    public static void applyToScene(Scene scene)
    {
        String cssUrl = ThemeManager.class.getResource(CSS_PATH).toExternalForm();
        if (!scene.getStylesheets().contains(cssUrl))
        {
            scene.getStylesheets().add(cssUrl);
        }
        applyRootStyle(scene.getRoot());
    }

    public static void applyRootStyle(Parent root)
    {
        if (root == null) return;

        double baseFontSize = LayoutScale.scaled(14.0);
        root.setStyle(String.format(java.util.Locale.US, "-fx-font-size: %.2fpx;", baseFontSize));

        root.getStyleClass().remove("dark");
        if (isDarkTheme)
        {
            root.getStyleClass().add("dark");
        }
    }

    public static void setDarkTheme(boolean dark)
    {
        isDarkTheme = dark;
        for (Window window : Window.getWindows())
        {
            if (window.getScene() != null) {
                applyRootStyle(window.getScene().getRoot());
            }
        }
        if (mapController != null)
        {
            mapController.updateMapTheme(dark);
        }
    }

    public static void saveDarkTheme(boolean dark)
    {
        setDarkTheme(dark);
        AppSettings.setDarkTheme(dark);
    }

    public static boolean isDarkTheme()
    {
        return isDarkTheme;
    }
}
