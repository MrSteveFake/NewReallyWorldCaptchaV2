package com.limbocaptcha.database;

import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

    private Connection connection;
    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager(String host, int port, String database, String user, String password) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true";
        this.user = user;
        this.password = password;
        connect();
    }

    private void connect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(url, user, password);
            createTables();
            System.out.println("[LimboCaptcha] MySQL connected!");
        } catch (Exception e) {
            System.err.println("[LimboCaptcha] MySQL error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS captcha_verifications (" +
            "id INT AUTO_INCREMENT PRIMARY KEY," +
            "player_uuid VARCHAR(36) NOT NULL," +
            "player_name VARCHAR(32) NOT NULL," +
            "token VARCHAR(128) NOT NULL UNIQUE," +
            "status VARCHAR(20) NOT NULL DEFAULT 'pending'," +
            "ip_address VARCHAR(45)," +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "completed_at TIMESTAMP NULL," +
            "INDEX idx_token (token)," +
            "INDEX idx_uuid (player_uuid)," +
            "INDEX idx_status (status)" +
            ")";
        connection.createStatement().execute(sql);
    }

    public void ensureConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
    }

    public String createVerification(UUID playerUuid, String playerName, String token, String ip) {
        ensureConnection();
        try {
            // Удаляем старые записи
            PreparedStatement del = connection.prepareStatement(
                "DELETE FROM captcha_verifications WHERE player_uuid = ? OR token = ?"
            );
            del.setString(1, playerUuid.toString());
            del.setString(2, token);
            del.executeUpdate();

            // Создаем новую
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
        ensureConnection();
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM captcha_verifications WHERE token = ? AND status = 'pending'"
            );
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public String getPlayerUUIDByToken(String token) {
        ensureConnection();
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
        ensureConnection();
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
        ensureConnection();
        try {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE captcha_verifications SET status = 'success', completed_at = NOW() WHERE token = ?"
            );
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void markFailed(String token) {
        ensureConnection();
        try {
            PreparedStatement ps = connection.prepareStatement(
                "UPDATE captcha_verifications SET status = 'failed', completed_at = NOW() WHERE token = ?"
            );
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerNameByToken(String token) {
        ensureConnection();
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

    // Метод для опроса статуса из плагина
    public String pollStatus(String token) {
        ensureConnection();
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM captcha_verifications WHERE token = ? AND status != 'pending'"
            );
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("status");
        } catch (SQLException e) {}
        return null;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {}
    }
}
