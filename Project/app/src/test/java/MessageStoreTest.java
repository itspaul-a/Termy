import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MessageStoreTest {

    private MessageStore store;

    @Before
    public void setUp() throws SQLException {
        // Use an in-memory SQLite database for fast, isolated tests
        store = new MessageStore(":memory:");
    }

    @After
    public void tearDown() throws SQLException {
        if (store != null) {
            store.close();
        }
    }

    @Test
    public void testSaveAndCountMessages() throws SQLException {
        assertEquals(0, store.getMessageCount());

        StoredMessage msg = store.saveMessage("Alice", null, "general", "Hello world");
        assertNotNull(msg);
        assertTrue(msg.id() > 0);
        assertEquals("Alice", msg.sender());
        assertEquals("general", msg.room());
        assertEquals("Hello world", msg.content());
        assertEquals(1, store.getMessageCount());
    }

    @Test
    public void testGetRecentRoomMessagesOrderingAndLimit() throws SQLException {
        for (int i = 1; i <= 10; i++) {
            store.saveMessage("User" + i, null, "general", "Message " + i);
        }

        // Fetch last 3 messages
        List<StoredMessage> recent = store.getRecentRoomMessages("general", 3);
        assertEquals(3, recent.size());

        // Should be in chronological order: Message 8, Message 9, Message 10
        assertEquals("Message 8", recent.get(0).content());
        assertEquals("Message 9", recent.get(1).content());
        assertEquals("Message 10", recent.get(2).content());
    }

    @Test
    public void testRoomIsolation() throws SQLException {
        store.saveMessage("Alice", null, "general", "Hi in general");
        store.saveMessage("Bob", null, "random", "Hi in random");

        List<StoredMessage> generalMsgs = store.getRecentRoomMessages("general", 10);
        assertEquals(1, generalMsgs.size());
        assertEquals("Hi in general", generalMsgs.get(0).content());

        List<StoredMessage> randomMsgs = store.getRecentRoomMessages("random", 10);
        assertEquals(1, randomMsgs.size());
        assertEquals("Hi in random", randomMsgs.get(0).content());
    }

    @Test
    public void testDirectMessageHistory() throws SQLException {
        store.saveMessage("Alice", "Bob", null, "Hey Bob!");
        store.saveMessage("Bob", "Alice", null, "Hey Alice, what's up?");
        store.saveMessage("Charlie", "Bob", null, "Charlie to Bob whisper");

        // Alice & Bob conversation
        List<StoredMessage> convo = store.getRecentDirectMessages("Alice", "Bob", 10);
        assertEquals(2, convo.size());
        assertEquals("Hey Bob!", convo.get(0).content());
        assertEquals("Hey Alice, what's up?", convo.get(1).content());
    }

    @Test
    public void testSearchMessages() throws SQLException {
        store.saveMessage("Alice", null, "general", "The quick brown fox");
        store.saveMessage("Bob", null, "general", "jumps over the lazy dog");
        store.saveMessage("Alice", null, "general", "Another brown bear");

        List<StoredMessage> matches = store.searchMessages("brown", 10);
        assertEquals(2, matches.size());
    }
}
