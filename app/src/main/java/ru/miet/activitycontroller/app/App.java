package ru.miet.activitycontroller.app;

import javafx.application.Application;
import ru.miet.activitycontroller.core.Pinger;
import ru.miet.activitycontroller.ui.Viewer;

public class App 
{
    public static void main( String[] args )
    {
        Application.launch(Viewer.class, args);
    }
}
