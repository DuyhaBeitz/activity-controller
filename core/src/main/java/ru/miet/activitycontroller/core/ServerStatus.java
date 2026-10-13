package ru.miet.activitycontroller.core;

public enum ServerStatus {
    OK,
    WARNING,
    ERROR,
    CONTROL;

    public static ServerStatus DetermineServerStatus(Pinger.PingResult ping) {
        if (ping == null) {return ERROR;}
        if (ping.httpCode() < 200 || ping.httpCode() >= 400) {return ERROR;}
        if (ping.totalTime() > 3.0) {return WARNING;}
        return OK;
    }
}