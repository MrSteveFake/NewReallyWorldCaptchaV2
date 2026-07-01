package com.limbocaptcha.database;

import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

    private Connection c;

    public DatabaseManager(Path dir) {
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("database.db"));
            c.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS captcha_verifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT, player_name TEXT, token TEXT UNIQUE," +
                "status TEXT DEFAULT 'pending', ip_address TEXT," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "completed_at TIMESTAMP)"
            );
            System.out.println("[LimboCaptcha] SQLite OK");
        } catch (Exception e) {
            System.err.println("[LimboCaptcha] SQLite error: " + e.getMessage());
        }
    }

    public String createVerification(UUID uuid, String name, String token, String ip) {
        try {
            c.prepareStatement("DELETE FROM captcha_verifications WHERE player_uuid='" + uuid + "'").executeUpdate();
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO captcha_verifications (player_uuid, player_name, token, status, ip_address) VALUES (?,?,?,'pending',?)"
            );
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, token);
            ps.setString(4, ip);
            ps.executeUpdate();
            return token;
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public boolean isValidToken(String t) {
        try {
            return c.prepareStatement("SELECT id FROM captcha_verifications WHERE token='" + t + "' AND status='pending'").executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public String getPlayerUUIDByToken(String t) {
        try {
            ResultSet rs = c.prepareStatement("SELECT player_uuid FROM captcha_verifications WHERE token='" + t + "'").executeQuery();
            if (rs.next()) return rs.getString("player_uuid");
        } catch (SQLException e) {}
        return null;
    }

    public String pollStatus(String t) {
        try {
            ResultSet rs = c.prepareStatement("SELECT status FROM captcha_verifications WHERE token='" + t + "' AND status!='pending'").executeQuery();
            if (rs.next()) return rs.getString("status");
        } catch (SQLException e) {}
        return null;
    }

    public void markSuccess(String t) {
        try { c.prepareStatement("UPDATE captcha_verifications SET status='success', completed_at=CURRENT_TIMESTAMP WHERE token='" + t + "'").executeUpdate(); } catch (SQLException e) {}
    }

    public void markFailed(String t) {
        try { c.prepareStatement("UPDATE captcha_verifications SET status='failed', completed_at=CURRENT_TIMESTAMP WHERE token='" + t + "'").executeUpdate(); } catch (SQLException e) {}
    }

    public String getPlayerNameByToken(String t) {
        try {
            ResultSet rs = c.prepareStatement("SELECT player_name FROM captcha_verifications WHERE token='" + t + "'").executeQuery();
            if (rs.next()) return rs.getString("player_name");
        } catch (SQLException e) {}
        return "Unknown";
    }

    public void close() {
        try { if (c != null && !c.isClosed()) c.close(); } catch (SQLException e) {}
    }
}
