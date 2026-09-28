package ru.miet.activitycontroller.app;

import ru.miet.activitycontroller.core.Pinger;

public class App 
{
    public static void main( String[] args )
    {
        Pinger.PingResult result = Pinger.runPing("https://www.youtube.com");
    }
}
