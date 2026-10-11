import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQLite-backed persistence store for chat messages.
 * Thread-safe for concurrent read and write operations across multiple client threads.
 */
public class MessageStore implements AutoCloseable {

    private final String jdbcUrl;
    private Connection connection;
    private final Object dbLock = new Object();

    /**
     * Creates a MessageStore connected to the given file path.
     *
     * @param dbPath Path to the SQLite database file (e.g. "termy.db" or ":memory:")
     * @throws SQLException if connecting or initializing the schema fails
     */
    public MessageStore(String dbPath) throws SQLException {
        if (!":memory:".equals(dbPath)) {
            File file = new File(dbPath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }

        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
        this.connection = DriverManager.getConnection(jdbcUrl);

        initDatabase();
    }

    private void initDatabase() throws SQLException {
        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement()) {
                // WAL mode improves concurrency by allowing readers while a write is occurring
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp INTEGER NOT NULL,
                        sender TEXT NOT NULL,
                        recipient TEXT,
                        room TEXT,
                        content TEXT NOT NULL
                    );
                """);

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_room_time ON messages(room, timestamp DESC);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_dm ON messages(sender, recipient, timestamp DESC);");
            }
        }
    }

    /**
     * Persists a new chat message into SQLite.
     *
     * @param sender    Username of sender
     * @param recipient Username of recipient (nullable for public/room messages)
     * @param room      Room or channel name (e.g. "general", nullable for DMs)
     * @param content   Message body
     * @return StoredMessage record with generated ID and timestamp
     * @throws SQLException if database write fails
     */
    public StoredMessage saveMessage(String sender, String recipient, String room, String content) throws SQLException {
        Instant now = Instant.now();
        long epochMillis = now.toEpochMilli();

        String sql = "INSERT INTO messages (timestamp, sender, recipient, room, content) VALUES (?, ?, ?, ?, ?)";

        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setLong(1, epochMillis);
                pstmt.setString(2, sender);
                pstmt.setString(3, recipient);
                pstmt.setString(4, room);
                pstmt.setString(5, content);
                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long generatedId = rs.getLong(1);
                        return new StoredMessage(generatedId, now, sender, recipient, room, content);
                    }
                }
            }
        }
        return new StoredMessage(-1, now, sender, recipient, room, content);
    }

    /**
     * Retrieves the most recent messages for a room in chronological order (oldest to newest).
     *
     * @param room  Room name (e.g. "general")
     * @param limit Maximum number of messages to fetch
     * @return List of messages in chronological order
     * @throws SQLException if database read fails
     */
    public List<StoredMessage> getRecentRoomMessages(String room, int limit) throws SQLException {
        String sql = """
            SELECT id, timestamp, sender, recipient, room, content
            FROM messages
            WHERE room = ?
            ORDER BY timestamp DESC, id DESC
            LIMIT ?
        """;

        List<StoredMessage> result = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, room);
                pstmt.setInt(2, limit);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                }
            }
        }

        // Reverse so that callers receive messages in natural chronological order
        Collections.reverse(result);
        return result;
    }

    /**
     * Retrieves direct message history between two users in chronological order.
     *
     * @param user1 First user
     * @param user2 Second user
     * @param limit Maximum number of messages to fetch
     * @return List of messages in chronological order
     * @throws SQLException if database read fails
     */
    public List<StoredMessage> getRecentDirectMessages(String user1, String user2, int limit) throws SQLException {
        String sql = """
            SELECT id, timestamp, sender, recipient, room, content
            FROM messages
            WHERE (sender = ? AND recipient = ?) OR (sender = ? AND recipient = ?)
            ORDER BY timestamp DESC, id DESC
            LIMIT ?
        """;

        List<StoredMessage> result = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, user1);
                pstmt.setString(2, user2);
                pstmt.setString(3, user2);
                pstmt.setString(4, user1);
                pstmt.setInt(5, limit);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                }
            }
        }

        Collections.reverse(result);
        return result;
    }

    /**
     * Searches messages across all rooms and direct messages by substring match.
     *
     * @param query Search query substring
     * @param limit Maximum results to return
     * @return Matching messages in descending order (most recent first)
     * @throws SQLException if database search fails
     */
    public List<StoredMessage> searchMessages(String query, int limit) throws SQLException {
        String sql = """
            SELECT id, timestamp, sender, recipient, room, content
            FROM messages
            WHERE content LIKE ?
            ORDER BY timestamp DESC, id DESC
            LIMIT ?
        """;

        List<StoredMessage> result = new ArrayList<>();
        synchronized (dbLock) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, "%" + query + "%");
                pstmt.setInt(2, limit);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns total count of stored messages.
     */
    public int getMessageCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM messages";
        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Deletes all stored messages (useful for test resets).
     */
    public void clearAllMessages() throws SQLException {
        synchronized (dbLock) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM messages");
            }
        }
    }

    private StoredMessage mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long epochMillis = rs.getLong("timestamp");
        String sender = rs.getString("sender");
        String recipient = rs.getString("recipient");
        String room = rs.getString("room");
        String content = rs.getString("content");
        return new StoredMessage(id, Instant.ofEpochMilli(epochMillis), sender, recipient, room, content);
    }

    @Override
    public void close() throws SQLException {
        synchronized (dbLock) {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }
    }
}
