package ru.miet.activitycontroller.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database implements AutoCloseable {

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
                + " coordinates TEXT NOT NULL\n"
                + ");";

        try (var stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }


    public void insertServer(String name, String coordinates) throws SQLException {
        String sql = """
            INSERT INTO servers(name, coordinates)
            VALUES (?, ?)
            """;

        try (var pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, coordinates);
            pstmt.executeUpdate();
        }
    }
}
