package ru.miet.activitycontroller.app;

import javafx.application.Application;
import ru.miet.activitycontroller.core.Database;
import ru.miet.activitycontroller.core.Pinger;
import ru.miet.activitycontroller.ui.Viewer;
import java.sql.SQLException;

public class App 
{
    public static void main( String[] args ) throws SQLException
    {
        Application.launch(Viewer.class, args);
    }
}
