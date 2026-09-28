package ru.miet.activitycontroller.app;

import ru.miet.activitycontroller.core.Pinger;

public class App 
{
    public static void main( String[] args )
    {
        Pinger.PingResult result = Pinger.runPing("https://www.youtube.com");
        System.out.println(result.httpCode());
        System.out.println(result.dnsTime());
        System.out.println(result.tcpConnectTime());
        System.out.println(result.tlsTime());
        System.out.println(result.preTransferTime());
        System.out.println(result.redirectTime());
        System.out.println(result.ttfb());
        System.out.println(result.totalTime());
        System.out.println(result.size());
        System.out.println(result.downloadSpeed());
        System.out.println(result.remoteIp());
        System.out.println(result.remotePort());
        System.out.println(result.effectiveUrl());
    }
}
