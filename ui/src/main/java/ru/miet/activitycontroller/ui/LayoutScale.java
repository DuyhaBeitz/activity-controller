package ru.miet.activitycontroller.ui;

import javafx.stage.Screen;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ObservableDoubleValue;

import java.awt.*;

public class LayoutScale
{
    private static final double SCREEN_SCALE;

    static {
        double scale = 1.0;
        try
        {
            double screenWidth = Toolkit.getDefaultToolkit().getScreenSize().getWidth();
            scale = screenWidth / 1920.0;
        }
        catch(Exception e)
        {
            System.out.println("Ошибка получения размеров");
        }

        SCREEN_SCALE = scale;
    }

    public static double getScreenScale()
    {
        return SCREEN_SCALE;
    }

    public static double scaled(double baseValue)
    {
        return baseValue * SCREEN_SCALE;
    }
}
