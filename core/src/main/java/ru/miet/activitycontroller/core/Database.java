package ru.miet.activitycontroller.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Database implements AutoCloseable {

    public record ServerResult(
            int id,
            String name,
            float latitude,
            float longitude
    ) {}

    private static final String url = "jdbc:sqlite:sample.db";
    private final Connection connection;

    public Database() throws SQLException {
        connection = DriverManager.getConnection(url);
        createNewTable();
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private void createNewTable() {
        String sql = "CREATE TABLE IF NOT EXISTS servers (\n"
                + " id INTEGER PRIMARY KEY,\n"
                + " name TEXT NOT NULL,\n"
                + " latitude REAL NOT NULL,\n"
                + " longitude REAL NOT NULL\n"
                + ");";

        try (var stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public List<ServerResult> getServers() throws SQLException {
        List<ServerResult> list =  new ArrayList<>();
        var sql = "SELECT id, name, capacity FROM servers";

        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new ServerResult(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getFloat("latitude"),
                        rs.getFloat("longitude")
                ));
            }
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return list;
    }

    public void insertServer(String name, float latitude, float longitude) throws SQLException {
        String sql = """
            INSERT INTO servers(name, latitude, longitude)
            VALUES (?, ?, ?)
            """;

        try (var pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setFloat(2, latitude);
            pstmt.setFloat(3, longitude);
            pstmt.executeUpdate();
        }
    }
}
