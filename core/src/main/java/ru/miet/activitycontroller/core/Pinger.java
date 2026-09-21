package ru.miet.activitycontroller.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class Pinger {
    /**
     * Runs tcping against a target and returns its JSON output as a String.
     *
     * @param host    The hostname or IP address to probe (e.g., "1.1.1.1")
     * @param port    The TCP port to probe (e.g., 22 for SSH)
     * @return The raw JSON output from tcping, or null if an error occurred.
     */
    public static String runTcpingJson(String host, int port) {
        String command = String.format("tcping %s %d -j", host, port);

        try {
            ProcessBuilder pb = new ProcessBuilder(command.split(" "));
            Process process = pb.start();

            // Capture the JSON output from stdout
            String jsonOutput;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                jsonOutput = reader.lines().collect(Collectors.joining("\n"));
            }

            // Capture stderr for debugging/logging
            try (BufferedReader errorReader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String errorOutput = errorReader.lines().collect(Collectors.joining("\n"));
                if (!errorOutput.isEmpty()) {
                    System.err.println("tcping stderr: " + errorOutput);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("tcping exited with code: " + exitCode);
                // Depending on your needs, you might still want to return the partial output.
            }

            return jsonOutput;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}