package com.limbocaptcha.database;

import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

    private Connection connection;

    public DatabaseManager(Path dataDirectory) {
        try {
            Path dbPath = dataDirectory.resolve("database.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toString());
            createTables();
            System.out.println("[LimboCaptcha] Database connected!");
        } catch (Exception e) {
            System.err.println("[LimboCaptcha] Database error: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS captcha_verifications (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "player_uuid TEXT NOT NULL," +
            "player_name TEXT NOT NULL," +
            "token TEXT NOT NULL UNIQUE," +
            "status TEXT NOT NULL DEFAULT 'pending'," +
            "ip_address TEXT," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "completed_at TIMESTAMP" +
            ")";
        connection.createStatement().execute(sql);
    }

    public String createVerification(UUID playerUuid, String playerName, String token, String ip) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO captcha_verifications (player_uuid, player_name, token, status, ip_address) VALUES (?, ?, ?, 'pending', ?)"
            );
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, token);
            ps.setString(4, ip);
            ps.executeUpdate();
            return token;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean isValidToken(String token) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM captcha_verifications WHERE token = ? AND status = 'pending'"
            );
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public String getPlayerUUIDByToken(String token) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT player_uuid FROM captcha_verifications WHERE token = ?"
            );
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("player_uuid");
        } catch (SQLException e) {}
        return null;
    }

    public String getTokenStatus(String token) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM captcha_verifications WHERE token = ?"
            );
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("status");
        } catch (SQLException e) {}
        return null;
    }

    public void markSuccess(String token) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE captcha_verifications SET status = 'success', completed_at = CURRENT_TIMESTAMP WHERE token = ?"
            );
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void markFailed(String token) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE captcha_verifications SET status = 'failed', completed_at = CURRENT_TIMESTAMP WHERE token = ?"
            );
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerNameByToken(String token) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT player_name FROM captcha_verifications WHERE token = ?"
            );
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("player_name");
        } catch (SQLException e) {}
        return "Unknown";
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {}
    }
}
