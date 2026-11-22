package ru.miet.activitycontroller.core;

import java.util.prefs.Preferences;

public class AppSettings
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(AppSettings.class);

    private static final String KEY_DARK_THEME = "dark_theme";
    // private static final String KEY_POLLING_INTERVAL = "polling_interval_sec";
    // private static final String KEY_WARNING_THRESHOLD = "warning_threshold_sec";

    private static boolean darkTheme;
    // private static int pollingIntervalSec;
    // private static double warningThresholdSec;

    // Загружаем сохранённые настройки при первом обращении к классу
    static
    {
        darkTheme = PREFS.getBoolean(KEY_DARK_THEME, false);
        // pollingIntervalSec = PREFS.getInt(KEY_POLLING_INTERVAL, 5);
        // warningThresholdSec = PREFS.getDouble(KEY_WARNING_THRESHOLD, 3.0);
    }

    public static boolean isDarkTheme()
    {
        return darkTheme;
    }

    public static void setDarkTheme(boolean dark)
    {
        darkTheme = dark;
        PREFS.putBoolean(KEY_DARK_THEME, dark);
    }

    // public static int getPollingIntervalSec()
    // {
    //     return pollingIntervalSec;
    // }

    // public static void setPollingIntervalSec(int seconds)
    // {
    //     pollingIntervalSec = seconds;
    //     PREFS.putInt(KEY_POLLING_INTERVAL, seconds);
    // }

    // public static double getWarningThresholdSec()
    // {
    //     return warningThresholdSec;
    // }

    // public static void setWarningThresholdSec(double seconds)
    // {
    //     warningThresholdSec = seconds;
    //     PREFS.putDouble(KEY_WARNING_THRESHOLD, seconds);
    // }
}