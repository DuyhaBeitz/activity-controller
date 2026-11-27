package ru.miet.activitycontroller.core;

public enum ServerStatus {
    OK,
    WARNING,
    ERROR,
    CONTROL;

    public static ServerStatus DetermineServerStatus(Pinger.PingResult ping) {
        if (ping == null) {return ERROR;}
        if (ping.httpCode() < 200 || ping.httpCode() >= 400) {return ERROR;}
        if (ping.ttfb() > AppSettings.getWarningThresholdSec()) {return WARNING;}
        return OK;
    }
}