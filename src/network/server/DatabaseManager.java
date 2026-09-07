package network.server;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class DatabaseManager {

    private Connection connection;

    public DatabaseManager(String dbPath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initTables();
            System.out.println("✅ [Database] SQLite database initialized successfully at: " + dbPath);
        } catch (SQLException e) {
            System.err.println("❌ [Database] Failed to initialize database: " + e.getMessage());
        }
    }

    private void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS game_sessions (
                    id TEXT PRIMARY KEY,
                    state_json TEXT,
                    created_at INTEGER,
                    last_updated INTEGER
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT PRIMARY KEY,
                    session_id TEXT,
                    sender_id TEXT,
                    text TEXT,
                    timestamp INTEGER
                )
            """);
        }
    }

    // 🔴 FIX M-24: متد هش‌کننده امن و استاندارد PBKDF2
    private String hashPassword(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = factory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }

    public synchronized boolean registerOrAuthenticate(String clientId, String username, String password) {
        String queryCheck = "SELECT password_hash FROM players WHERE username = ?";
        try (PreparedStatement pstmtCheck = connection.prepareStatement(queryCheck)) {
            pstmtCheck.setString(1, username);
            ResultSet rs = pstmtCheck.executeQuery();

            if (rs.next()) {
                String storedData = rs.getString("password_hash");
                String[] parts = storedData.split("\\$");
                if (parts.length != 2) return false;

                byte[] salt = Base64.getDecoder().decode(parts[0]);
                String expectedHash = parts[1];
                try {
                    String providedHash = hashPassword(password, salt);
                    return expectedHash.equals(providedHash);
                } catch (Exception e) {
                    return false;
                }
            } else {
                String queryInsert = "INSERT INTO players (id, username, password_hash, created_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmtInsert = connection.prepareStatement(queryInsert)) {
                    byte[] salt = new byte[16];
                    new SecureRandom().nextBytes(salt);
                    String hash = hashPassword(password, salt);
                    String dbStorable = Base64.getEncoder().encodeToString(salt) + "$" + hash;

                    pstmtInsert.setString(1, clientId);
                    pstmtInsert.setString(2, username);
                    pstmtInsert.setString(3, dbStorable);
                    pstmtInsert.setLong(4, System.currentTimeMillis());
                    pstmtInsert.executeUpdate();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ [Database] Auth error for user " + username + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized void saveGameSession(String sessionId, String stateJson) {
        String query = """
            INSERT INTO game_sessions (id, state_json, created_at, last_updated) 
            VALUES (?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET state_json = ?, last_updated = ?
        """;
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            long now = System.currentTimeMillis();
            pstmt.setString(1, sessionId);
            pstmt.setString(2, stateJson);
            pstmt.setLong(3, now);
            pstmt.setLong(4, now);
            pstmt.setString(5, stateJson);
            pstmt.setLong(6, now);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ [Database] Failed to save game session: " + e.getMessage());
        }
    }

    public synchronized String loadGameSession(String sessionId) {
        String query = "SELECT state_json FROM game_sessions WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, sessionId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("state_json");
            }
        } catch (SQLException e) {
            System.err.println("❌ [Database] Failed to load session: " + e.getMessage());
        }
        return null;
    }

    public synchronized List<String> getAllSessionIds() {
        List<String> ids = new ArrayList<>();
        String query = "SELECT id FROM game_sessions ORDER BY last_updated DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
        } catch (SQLException e) {
            System.err.println("❌ [Database] Failed to fetch session IDs: " + e.getMessage());
        }
        return ids;
    }

    public synchronized void saveChatMessage(String sessionId, String senderId, String text) {
        String query = "INSERT INTO chat_messages (id, session_id, sender_id, text, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, UUID.randomUUID().toString());
            pstmt.setString(2, sessionId);
            pstmt.setString(3, senderId);
            pstmt.setString(4, text);
            pstmt.setLong(5, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ [Database] Failed to save chat message: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("🔒 [Database] Connection closed.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}