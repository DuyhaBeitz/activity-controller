package ru.miet.activitycontroller.app;

import ru.miet.activitycontroller.core.Pinger;

public class App 
{
    public static void main( String[] args )
    {
        String result = Pinger.runTcpingJson("1.1.1.1", 22);
        System.out.println(result);
    }
}
