package ru.miet.activitycontroller.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stores ping history for every monitored server.
 *
 * Every entry is one line of JSON (JSONL) in {@code data/<id>/history.jsonl}.
 * New results are appended to the end of the file; existing lines are never rewritten.
 * Entries are therefore always in chronological order (oldest first).
 */
public class PingHistory {

    private static final String DATA_DIR = "data";
    private static final String HISTORY_FILE_NAME = "history.jsonl";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // ISO-8601 strings, not arrays
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public record HistoryEntry(LocalDateTime timestamp, Pinger.PingResult ping) {
    }

    private PingHistory() {
    }

    // Writing

    public static void AppendWrite(int id, Pinger.PingResult pingResult) throws IOException {
        AppendWrite(id, pingResult, LocalDateTime.now());
    }

    public static void AppendWrite(int id, Pinger.PingResult pingResult, LocalDateTime timestamp) throws IOException {
        if (pingResult == null) {
            return;
        }

        Path historyFile = HistoryFile(id);
        Files.createDirectories(historyFile.getParent());

        String line = MAPPER.writeValueAsString(new HistoryEntry(timestamp, pingResult)) + "\n";
        Files.writeString(historyFile, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public static List<HistoryEntry> GetLastN(int id, int count) throws IOException {
        List<HistoryEntry> all = ReadAll(id);
        int fromIndex = Math.max(0, all.size() - Math.max(0, count));
        return all.subList(fromIndex, all.size());
    }

    public static List<HistoryEntry> GetBetween(int id, LocalDateTime from, LocalDateTime to) throws IOException {
        List<HistoryEntry> result = new ArrayList<>();
        for (HistoryEntry entry : ReadAll(id)) {
            LocalDateTime ts = entry.timestamp();
            if (!ts.isBefore(from) && ts.isBefore(to)) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<HistoryEntry> GetSince(int id, Duration period) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        return GetBetween(id, now.minus(period), now.plusNanos(1)); // +1ns so "now" itself is included
    }

    public static Double AverageResponseTime(int id, int count) throws IOException {
        List<HistoryEntry> latest = GetLastN(id, count);

        if (latest.isEmpty()) {
            return null;
        }

        double sum = 0.0;
        for (HistoryEntry entry : latest) {
            sum += entry.ping().totalTime();
        }
        return sum / latest.size();
    }

    public static List<Database.ServerResult> GetSortedServersByAvgResponseTime(Database database, int count) throws IOException {
        try {
            List<Database.ServerResult> res = database.getServers();
            res.sort(Comparator.comparingDouble(
                    server -> {
                        try {
                            Double avg = AverageResponseTime(server.id(), count);
                            // No history -> 0.0 (goes first). Use Double.MAX_VALUE to put them last.
                            return avg != null ? avg : 0.0;
                        } catch (IOException e) {
                            return 0.0;
                        }
                    }
            ));
            return res;
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return new ArrayList<>();
    }

    // Internals

    private static Path HistoryFile(int id) {
        return Paths.get(DATA_DIR, String.valueOf(id), HISTORY_FILE_NAME);
    }

    private static List<HistoryEntry> ReadAll(int id) throws IOException {
        Path historyFile = HistoryFile(id);
        List<HistoryEntry> entries = new ArrayList<>();

        if (!Files.isRegularFile(historyFile)) {
            return entries;
        }

        for (String line : Files.readAllLines(historyFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                entries.add(MAPPER.readValue(line, HistoryEntry.class));
            }
        }
        return entries;
    }
}