package ru.miet.activitycontroller.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Pinger {

    public record PingResult(
        int httpCode,
        double dnsTime,
        double tcpConnectTime,
        double tlsTime,
        double preTransferTime,
        double redirectTime,
        double ttfb,
        double totalTime,
        long size,
        double downloadSpeed,
        String remoteIp,
        int remotePort,
        String effectiveUrl
    ) {}

    /**
     * @param host    The hostname or IP address to probe (e.g., "1.1.1.1")
     * @return The raw JSON output from tcping, or null if an error occurred.
     */
    public static PingResult runPing(String host) {
        try {
            Process process = new ProcessBuilder(
                "curl",
                "-sS",
                "-L",
                "-o", "/dev/null",
                "-w",
                "%{http_code}\t" +
                "%{time_namelookup}\t" +
                "%{time_connect}\t" +
                "%{time_appconnect}\t" +
                "%{time_pretransfer}\t" +
                "%{time_redirect}\t" +
                "%{time_starttransfer}\t" +
                "%{time_total}\t" +
                "%{size_download}\t" +
                "%{speed_download}\t" +
                "%{remote_ip}\t" +
                "%{remote_port}\t" +
                "%{url_effective}\n",
                host
            ).start();

            String line;

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }

            int exitCode = process.waitFor();

            if (exitCode != 0 || line == null) {
                return null;
            }

            String[] p = line.split("\t", -1);

            return new PingResult(
                Integer.parseInt(p[0]),
                Double.parseDouble(p[1]),
                Double.parseDouble(p[2]),
                Double.parseDouble(p[3]),
                Double.parseDouble(p[4]),
                Double.parseDouble(p[5]),
                Double.parseDouble(p[6]),
                Double.parseDouble(p[7]),
                Long.parseLong(p[8]),
                Double.parseDouble(p[9]),
                p[10],
                Integer.parseInt(p[11]),
                p[12]
            );

        } catch (Exception e) {
            return null;
        }
    }
}