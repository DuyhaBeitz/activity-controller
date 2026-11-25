package ru.miet.activitycontroller.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stores ping history for every monitored server.
 *
 * Every entry is one line of JSON (JSONL) in {@code data/<id>/history.jsonl}.
 * New results are appended to the end of the file; existing lines are never rewritten.
 */
public class PingHistory {

    private static final String DATA_DIR = "data";
    private static final String HISTORY_FILE_NAME = "history.jsonl";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PingHistory() {
    }

    /**
     * Appends the given ping result to the server's history log.
     *
     * @param id          Identifier of the monitored server
     * @param pingResult  The ping result to append, or null if the ping failed
     * @throws IOException if the history file cannot be written
     */
    public static void AppendWrite(int id, Pinger.PingResult pingResult) throws IOException {
        AppendWrite(id, pingResult, LocalDateTime.now());
    }

    /**
     * Appends the given ping result with an explicit timestamp to the server's history log.
     *
     * @param id          Identifier of the monitored server
     * @param pingResult  The ping result to append, or null if the ping failed
     * @param timestamp   Timestamp of the ping result
     * @throws IOException if the history file cannot be written
     */
    public static void AppendWrite(int id, Pinger.PingResult pingResult, LocalDateTime timestamp) throws IOException {
        if (pingResult == null) {
            // A failed ping has no metrics to persist, nothing to append.
            return;
        }

        Path historyFile = HistoryFile(id);
        Files.createDirectories(historyFile.getParent());

        String line = MAPPER.writeValueAsString(new HistoryEntry(timestamp.toString(), pingResult)) + "\n";
        Files.writeString(historyFile, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Returns the last {@code count} ping results for the given server.
     * If less than {@code count} results exist, all of them are returned.
     *
     * @param id    Identifier of the monitored server
     * @param count Maximum number of results to return
     * @return List of the last ping results (oldest first), never null
     * @throws IOException if the history file cannot be read
     */
    public static List<Pinger.PingResult> GetLastN(int id, int count) throws IOException {
        Path historyFile = HistoryFile(id);
        List<Pinger.PingResult> result = new ArrayList<>();

        if (!Files.isRegularFile(historyFile)) {
            return result;
        }

        List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
        int fromIndex = Math.max(0, lines.size() - Math.max(0, count));

        for (String line : lines.subList(fromIndex, lines.size())) {
            if (!line.isBlank()) {
                result.add(MAPPER.readValue(line, HistoryEntry.class).ping());
            }
        }
        return result;
    }

    /**
     * Returns the average response time (total time) for the given server,
     * calculated over the last {@code count} ping results.
     * If no results exist, returns null.
     *
     * @param id    Identifier of the monitored server
     * @param count Number of latest ping results to average over
     * @return Average response time, or null if there are no results
     * @throws IOException if the history file cannot be read
     */
    public static Double AverageResponseTime(int id, int count) throws IOException {
        List<Pinger.PingResult> latest = GetLastN(id, count);

        if (latest.isEmpty()) {
            return null;
        }

        double sum = 0.0;
        for (Pinger.PingResult ping : latest) {
            sum += ping.totalTime();
        }
        return sum / latest.size();
    }

    private static Path HistoryFile(int id) {
        return Paths.get(DATA_DIR, String.valueOf(id), HISTORY_FILE_NAME);
    }

    private record HistoryEntry(String timestamp, Pinger.PingResult ping) {
    }
}