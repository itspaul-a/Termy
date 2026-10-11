import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a persistent chat message stored in the database.
 *
 * @param id        Unique auto-incrementing database ID
 * @param timestamp Time when the message was stored (UTC Instant)
 * @param sender    Username of the sender
 * @param recipient Username of the recipient (nullable for broadcast/room messages)
 * @param room      Room or channel name (e.g., "general", nullable for direct messages)
 * @param content   Text payload of the message
 */
public record StoredMessage(
        long id,
        Instant timestamp,
        String sender,
        String recipient,
        String room,
        String content
) {
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Formats the timestamp using the local system time zone in HH:mm:ss format.
     */
    public String formattedTime() {
        return TIME_FORMATTER.format(timestamp);
    }
}
