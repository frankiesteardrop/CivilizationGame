package network.server;

import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

    private Connection connection;

    public DatabaseManager(String dbPath) {
        try {
            // اتصال به دیتابیس SQLite از طریق JDBC
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initTables();
            System.out.println("✅ [Database] SQLite database initialized successfully at: " + dbPath);
        } catch (SQLException e) {
            System.err.println("❌ [Database] Failed to initialize database: " + e.getMessage());
        }
    }

    private void initTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // جدول کاربران و احراز هویت
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    id TEXT PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """);

            // جدول ذخیره وضعیت سشن‌های بازی (برای قابلیت Save/Load سرور)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS game_sessions (
                    id TEXT PRIMARY KEY,
                    state_json TEXT,
                    created_at INTEGER,
                    last_updated INTEGER
                )
            """);

            // جدول تاریخچه چت‌ها
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

    public synchronized boolean registerOrAuthenticate(String clientId, String username, String password) {
        String queryCheck = "SELECT password_hash FROM players WHERE username = ?";
        try (PreparedStatement pstmtCheck = connection.prepareStatement(queryCheck)) {
            pstmtCheck.setString(1, username);
            ResultSet rs = pstmtCheck.executeQuery();

            if (rs.next()) {
                // کاربر از قبل وجود دارد -> بررسی صحت رمز عبور (ساده‌شده با hashCode برای مقیاس این پروژه)
                String storedHash = rs.getString("password_hash");
                return storedHash.equals(String.valueOf(password.hashCode()));
            } else {
                // کاربر جدید -> ثبت‌نام در دیتابیس
                String queryInsert = "INSERT INTO players (id, username, password_hash, created_at) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmtInsert = connection.prepareStatement(queryInsert)) {
                    pstmtInsert.setString(1, clientId);
                    pstmtInsert.setString(2, username);
                    pstmtInsert.setString(3, String.valueOf(password.hashCode()));
                    pstmtInsert.setLong(4, System.currentTimeMillis());
                    pstmtInsert.executeUpdate();
                    return true;
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