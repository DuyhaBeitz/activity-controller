package ru.miet.activitycontroller.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Stores ping history for every monitored server.
 *
 * Every entry is persisted as a single JSON object (ping metrics + timestamp)
 * inside a JSON array, written to {@code data/<id>/history.json} (created on demand).
 * The file grows like a log: each new {@link Pinger.PingResult} is appended.
 */
public class PingHistory {

    private static final String DATA_DIR = "data";
    private static final String HISTORY_FILE_NAME = "history.json";

    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_HTTP_CODE = "httpCode";
    private static final String FIELD_DNS_TIME = "dnsTime";
    private static final String FIELD_TCP_CONNECT_TIME = "tcpConnectTime";
    private static final String FIELD_TLS_TIME = "tlsTime";
    private static final String FIELD_PRE_TRANSFER_TIME = "preTransferTime";
    private static final String FIELD_REDIRECT_TIME = "redirectTime";
    private static final String FIELD_TTFB = "ttfb";
    private static final String FIELD_TOTAL_TIME = "totalTime";
    private static final String FIELD_SIZE = "size";
    private static final String FIELD_DOWNLOAD_SPEED = "downloadSpeed";
    private static final String FIELD_REMOTE_IP = "remoteIp";
    private static final String FIELD_REMOTE_PORT = "remotePort";
    private static final String FIELD_EFFECTIVE_URL = "effectiveUrl";

    private PingHistory() {
    }

    /**
     * Appends the given ping result to the server's history log.
     * Creates the {@code data/<id>/history.json} file if it does not exist yet.
     *
     * @param id           Identifier of the monitored server
     * @param pingResult  The ping result to append, or null if the ping failed
     * @throws IOException if the history file cannot be read or written
     */
    public static void AppendWrite(int id, Pinger.PingResult pingResult) throws IOException {
        AppendWrite(id, pingResult, LocalDateTime.now());
    }

    /**
     * Appends the given ping result with an explicit timestamp to the server's history log.
     * Creates the {@code data/<id>/history.json} file if it does not exist yet.
     *
     * @param id           Identifier of the monitored server
     * @param pingResult  The ping result to append, or null if the ping failed
     * @param timestamp   Timestamp of the ping result
     * @throws IOException if the history file cannot be read or written
     */
    public static void AppendWrite(int id, Pinger.PingResult pingResult, LocalDateTime timestamp) throws IOException {
        if (pingResult == null) {
            // A failed ping has no metrics to persist, nothing to append.
            return;
        }

        List<HistoryEntry> history = ReadAll(id);
        history.add(new HistoryEntry(pingResult, timestamp));

        Path historyDir = Paths.get(DATA_DIR, String.valueOf(id));
        Path historyFile = historyDir.resolve(HISTORY_FILE_NAME);

        Files.createDirectories(historyDir);
        Files.writeString(historyFile, ToJson(history), StandardCharsets.UTF_8);
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
        List<HistoryEntry> history = ReadAll(id);

        int total = history.size();
        int fromIndex = Math.max(0, total - Math.max(0, count));

        List<Pinger.PingResult> result = new ArrayList<>();
        for (int i = fromIndex; i < total; i++) {
            result.add(history.get(i).ping());
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

    /**
     * Returns a list of servers sorted by their average response time
     * calculated over the last {@code count} ping results.
     * If no results exist, returns empty.
     *
     * @param database    Existing Database for server lookups
     * @param count Number of latest ping results to average over
     * @return sorted list of servers
     * @throws IOException if the history file cannot be read
     */
    public static List<Database.ServerResult> GetSortedServersByAvgResponseTime(Database database, int count) throws IOException {
        try {
            List<Database.ServerResult> res = database.getServers();
            res.sort(Comparator.comparingDouble(
                    server -> {
                        try {
                            Double avg = AverageResponseTime(server.id(), count);
                            // Если истории нет, ставим 0.0 (будут в начале). 
                            // Если хочешь в конец — используй Double.MAX_VALUE.
                            return avg != null ? avg : 0.0; 
                        } catch (IOException e) {
                            return 0.0;
                        }
                    }
            ));
            return res;
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return new ArrayList<>();
    }

    private static List<HistoryEntry> ReadAll(int id) throws IOException {
        Path historyFile = Paths.get(DATA_DIR, String.valueOf(id), HISTORY_FILE_NAME);

        if (!Files.isRegularFile(historyFile)) {
            return new ArrayList<>();
        }

        String content = Files.readString(historyFile, StandardCharsets.UTF_8).trim();
        if (content.isEmpty()) {
            return new ArrayList<>();
        }

        Object parsed = new JsonParser(content).parse();
        List<HistoryEntry> history = new ArrayList<>();

        if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    history.add(new HistoryEntry(ToPingResult(map), ToTimestamp(map)));
                }
            }
        }

        return history;
    }

    private static LocalDateTime ToTimestamp(Map<?, ?> map) {
        Object ts = map.get(FIELD_TIMESTAMP);
        if (ts instanceof String value && !value.isEmpty()) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignored) {
                // fall through to default below
            }
        }
        return LocalDateTime.now();
    }

    private static Pinger.PingResult ToPingResult(Map<?, ?> map) {
        return new Pinger.PingResult(
                ((Number) map.get(FIELD_HTTP_CODE)).intValue(),
                ((Number) map.get(FIELD_DNS_TIME)).doubleValue(),
                ((Number) map.get(FIELD_TCP_CONNECT_TIME)).doubleValue(),
                ((Number) map.get(FIELD_TLS_TIME)).doubleValue(),
                ((Number) map.get(FIELD_PRE_TRANSFER_TIME)).doubleValue(),
                ((Number) map.get(FIELD_REDIRECT_TIME)).doubleValue(),
                ((Number) map.get(FIELD_TTFB)).doubleValue(),
                ((Number) map.get(FIELD_TOTAL_TIME)).doubleValue(),
                ((Number) map.get(FIELD_SIZE)).longValue(),
                ((Number) map.get(FIELD_DOWNLOAD_SPEED)).doubleValue(),
                (String) map.get(FIELD_REMOTE_IP),
                ((Number) map.get(FIELD_REMOTE_PORT)).intValue(),
                (String) map.get(FIELD_EFFECTIVE_URL)
        );
    }

    private static String ToJson(List<HistoryEntry> history) {
        if (history.isEmpty()) {
            return "[]";
        }

        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < history.size(); i++) {
            HistoryEntry entry = history.get(i);

            json.append("  {\n");
            json.append("    \"").append(FIELD_TIMESTAMP).append("\": ").append(JsonString(entry.timestamp().toString())).append(",\n");
            json.append("    \"").append(FIELD_HTTP_CODE).append("\": ").append(entry.ping().httpCode()).append(",\n");
            json.append("    \"").append(FIELD_DNS_TIME).append("\": ").append(entry.ping().dnsTime()).append(",\n");
            json.append("    \"").append(FIELD_TCP_CONNECT_TIME).append("\": ").append(entry.ping().tcpConnectTime()).append(",\n");
            json.append("    \"").append(FIELD_TLS_TIME).append("\": ").append(entry.ping().tlsTime()).append(",\n");
            json.append("    \"").append(FIELD_PRE_TRANSFER_TIME).append("\": ").append(entry.ping().preTransferTime()).append(",\n");
            json.append("    \"").append(FIELD_REDIRECT_TIME).append("\": ").append(entry.ping().redirectTime()).append(",\n");
            json.append("    \"").append(FIELD_TTFB).append("\": ").append(entry.ping().ttfb()).append(",\n");
            json.append("    \"").append(FIELD_TOTAL_TIME).append("\": ").append(entry.ping().totalTime()).append(",\n");
            json.append("    \"").append(FIELD_SIZE).append("\": ").append(entry.ping().size()).append(",\n");
            json.append("    \"").append(FIELD_DOWNLOAD_SPEED).append("\": ").append(entry.ping().downloadSpeed()).append(",\n");
            json.append("    \"").append(FIELD_REMOTE_IP).append("\": ").append(JsonString(entry.ping().remoteIp())).append(",\n");
            json.append("    \"").append(FIELD_REMOTE_PORT).append("\": ").append(entry.ping().remotePort()).append(",\n");
            json.append("    \"").append(FIELD_EFFECTIVE_URL).append("\": ").append(JsonString(entry.ping().effectiveUrl())).append("\n");
            json.append("  }");

            if (i < history.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("]\n");

        return json.toString();
    }

    private static String JsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private record HistoryEntry(Pinger.PingResult ping, LocalDateTime timestamp) {
    }

    /**
     * Minimal recursive-descent JSON parser (objects, arrays, numbers, strings, booleans, null).
     */
    private static final class JsonParser {

        private final String json;
        private int pos;

        JsonParser(String json) {
            this.json = json;
        }

        Object parse() {
            return parseValue();
        }

        private Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // consume '{'
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                pos++; // consume ':'
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    break;
                }
                throw new IllegalArgumentException("Unexpected character in JSON object: " + c);
            }
            return map;
        }

        private List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // consume '['
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    break;
                }
                throw new IllegalArgumentException("Unexpected character in JSON array: " + c);
            }
            return list;
        }

        private String parseString() {
            pos++; // consume opening '"'
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = json.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char escaped = json.charAt(pos++);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = json.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new IllegalArgumentException("Invalid JSON escape: \\" + escaped);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Number parseNumber() {
            int start = pos;
            while (pos < json.length()) {
                char c = json.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            String numberText = json.substring(start, pos);
            if (numberText.isEmpty()) {
                throw new IllegalArgumentException("Unexpected character in JSON: " + json.charAt(start));
            }
            if (numberText.contains(".") || numberText.contains("e") || numberText.contains("E")) {
                return (Number) Double.parseDouble(numberText);
            }
            return (Number) Long.parseLong(numberText);
        }

        private Object parseLiteral(String literal, Object value) {
            if (!json.startsWith(literal, pos)) {
                throw new IllegalArgumentException("Invalid JSON literal at position: " + pos);
            }
            pos += literal.length();
            return value;
        }

        private char peek() {
            if (pos >= json.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return json.charAt(pos);
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }
    }
}
