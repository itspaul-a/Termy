import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChatClient {

    public enum MessageType {
        SELF,
        PEER,
        SYSTEM,
        ERROR
    }

    public static class ChatMessage {
        final String timestamp;
        final String sender;
        final String text;
        final MessageType type;

        public ChatMessage(String sender, String text, MessageType type) {
            this.timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            this.sender = sender;
            this.text = text;
            this.type = type;
        }
    }

    private static class FormattedLine {
        final String timestamp;
        final String senderPrefix;
        final String content;
        final MessageType type;
        final boolean isContinuation;

        FormattedLine(String timestamp, String senderPrefix, String content, MessageType type, boolean isContinuation) {
            this.timestamp = timestamp;
            this.senderPrefix = senderPrefix;
            this.content = content;
            this.type = type;
            this.isContinuation = isContinuation;
        }
    }

    private final String host;
    private final int port;
    private String nickname = "You";
    private volatile boolean running = true;
    private volatile boolean connected = false;

    private Socket socket;
    private BufferedReader networkReader;
    private PrintWriter networkWriter;

    private Screen screen;
    private final Object renderLock = new Object();
    private final List<ChatMessage> messages = new ArrayList<>();
    private final StringBuilder inputBuffer = new StringBuilder();
    private int cursorIdx = 0;
    private int scrollOffset = 0;
    private volatile boolean needsRedraw = true;

    // Theme Colors
    private static final TextColor COLOR_HEADER_BG = new TextColor.RGB(25, 35, 60);
    private static final TextColor COLOR_BORDER = new TextColor.RGB(70, 85, 110);
    private static final TextColor COLOR_INPUT_BORDER = new TextColor.RGB(80, 140, 200);
    private static final TextColor COLOR_TIMESTAMP = new TextColor.RGB(130, 135, 150);
    private static final TextColor COLOR_HINT = new TextColor.RGB(120, 130, 145);

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void main(String[] args) {
        String hostname = (args.length > 0) ? args[0] : "localhost";
        int port = 5000;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        ChatClient client = new ChatClient(hostname, port);
        client.start();
    }

    public void start() {
        // Try connecting to the server
        try {
            socket = new Socket(host, port);
            networkReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            networkWriter = new PrintWriter(socket.getOutputStream(), true);
            connected = true;
        } catch (IOException e) {
            System.err.println("Could not connect to ChatServer at " + host + ":" + port);
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nPlease ensure ChatServer is running first.");
            return;
        }

        // Initialize Lanterna Screen
        try {
            DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
            Terminal terminal = terminalFactory.createTerminal();
            screen = new TerminalScreen(terminal);
            screen.startScreen();
            screen.clear();

            // Shutdown hook to cleanly restore terminal if process is killed
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));

            addMessage(new ChatMessage("System", "Connected to " + host + ":" + port + "! Type /help for commands.", MessageType.SYSTEM));

            // Start background receiver thread
            Thread receiveThread = new Thread(this::networkReceiveLoop, "Network-Receiver");
            receiveThread.setDaemon(true);
            receiveThread.start();

            // Main UI event loop
            uiEventLoop();

        } catch (IOException e) {
            System.err.println("Terminal UI Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void networkReceiveLoop() {
        try {
            String incoming;
            while (running && (incoming = networkReader.readLine()) != null) {
                if ("exit".equalsIgnoreCase(incoming.trim())) {
                    addMessage(new ChatMessage("System", "Server ended the chat session. Press F10 or type /exit to quit.", MessageType.SYSTEM));
                    connected = false;
                    requestRedraw();
                    break;
                }
                addMessage(new ChatMessage("Server", incoming, MessageType.PEER));
                requestRedraw();
            }
        } catch (IOException e) {
            if (running) {
                connected = false;
                addMessage(new ChatMessage("System", "Connection to server lost: " + e.getMessage(), MessageType.ERROR));
                requestRedraw();
            }
        }
    }

    private void uiEventLoop() throws IOException {
        while (running) {
            // Check terminal resize
            TerminalSize newSize = screen.doResizeIfNecessary();
            if (newSize != null) {
                needsRedraw = true;
            }

            // Process keyboard input
            KeyStroke keyStroke = screen.pollInput();
            if (keyStroke != null) {
                handleKeyStroke(keyStroke);
                needsRedraw = true;
            }

            // Redraw if state changed
            if (needsRedraw) {
                render();
                needsRedraw = false;
            }

            // Small sleep to keep CPU usage low while remaining ultra responsive
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleKeyStroke(KeyStroke key) {
        KeyType type = key.getKeyType();

        // Exit keys
        if (type == KeyType.F10 || type == KeyType.EOF) {
            sendExitAndClose();
            return;
        }

        if (key.isCtrlDown() && key.getCharacter() != null && (key.getCharacter() == 'c' || key.getCharacter() == 'C')) {
            sendExitAndClose();
            return;
        }

        switch (type) {
            case Character -> {
                Character ch = key.getCharacter();
                if (ch != null) {
                    inputBuffer.insert(cursorIdx, ch);
                    cursorIdx++;
                }
            }
            case Backspace -> {
                if (cursorIdx > 0) {
                    inputBuffer.deleteCharAt(cursorIdx - 1);
                    cursorIdx--;
                }
            }
            case Delete -> {
                if (cursorIdx < inputBuffer.length()) {
                    inputBuffer.deleteCharAt(cursorIdx);
                }
            }
            case ArrowLeft -> {
                if (cursorIdx > 0) {
                    cursorIdx--;
                }
            }
            case ArrowRight -> {
                if (cursorIdx < inputBuffer.length()) {
                    cursorIdx++;
                }
            }
            case Home -> cursorIdx = 0;
            case End -> cursorIdx = inputBuffer.length();
            case PageUp -> scrollOffset += 5;
            case PageDown -> scrollOffset = Math.max(0, scrollOffset - 5);
            case ArrowUp -> {
                if (inputBuffer.isEmpty()) {
                    scrollOffset++;
                }
            }
            case ArrowDown -> {
                if (inputBuffer.isEmpty()) {
                    scrollOffset = Math.max(0, scrollOffset - 1);
                }
            }
            case Enter -> handleSendInput();
            default -> {}
        }
    }

    private void handleSendInput() {
        String input = inputBuffer.toString().trim();
        inputBuffer.setLength(0);
        cursorIdx = 0;
        scrollOffset = 0; // jump back to bottom on send

        if (input.isEmpty()) {
            return;
        }

        // Check for slash commands
        if (input.startsWith("/")) {
            handleSlashCommand(input);
            return;
        }

        if (!connected) {
            addMessage(new ChatMessage("System", "Cannot send: Not connected to server.", MessageType.ERROR));
            return;
        }

        // Send over socket
        networkWriter.println(input);
        addMessage(new ChatMessage(nickname, input, MessageType.SELF));
    }

    private void handleSlashCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = (parts.length > 1) ? parts[1].trim() : "";

        switch (cmd) {
            case "/help" -> {
                addMessage(new ChatMessage("Help", "Available commands:", MessageType.SYSTEM));
                addMessage(new ChatMessage("Help", "  /nick <name>   - Change your display name", MessageType.SYSTEM));
                addMessage(new ChatMessage("Help", "  /clear         - Clear message history", MessageType.SYSTEM));
                addMessage(new ChatMessage("Help", "  /shrug         - Send ¯\\_(ツ)_/¯", MessageType.SYSTEM));
                addMessage(new ChatMessage("Help", "  /exit or /quit - Disconnect and quit", MessageType.SYSTEM));
                addMessage(new ChatMessage("Help", "Keys: [PgUp/PgDn] scroll | [F10] exit", MessageType.SYSTEM));
            }
            case "/clear" -> {
                synchronized (renderLock) {
                    messages.clear();
                }
                addMessage(new ChatMessage("System", "Chat history cleared.", MessageType.SYSTEM));
            }
            case "/nick" -> {
                if (arg.isEmpty()) {
                    addMessage(new ChatMessage("System", "Usage: /nick <new_name>", MessageType.ERROR));
                } else {
                    String oldNick = nickname;
                    nickname = arg;
                    addMessage(new ChatMessage("System", "Nickname changed from " + oldNick + " to " + nickname, MessageType.SYSTEM));
                }
            }
            case "/shrug" -> {
                String shrug = "¯\\_(ツ)_/¯";
                if (connected) {
                    networkWriter.println(shrug);
                    addMessage(new ChatMessage(nickname, shrug, MessageType.SELF));
                } else {
                    addMessage(new ChatMessage("System", "Cannot send: Not connected.", MessageType.ERROR));
                }
            }
            case "/exit", "/quit" -> sendExitAndClose();
            default -> addMessage(new ChatMessage("System", "Unknown command: " + cmd + ". Type /help for options.", MessageType.ERROR));
        }
    }

    private void sendExitAndClose() {
        if (connected && networkWriter != null) {
            try {
                networkWriter.println("exit");
            } catch (Exception ignored) {}
        }
        running = false;
    }

    public void addMessage(ChatMessage msg) {
        synchronized (renderLock) {
            messages.add(msg);
        }
        requestRedraw();
    }

    public void requestRedraw() {
        needsRedraw = true;
    }

    private void render() {
        synchronized (renderLock) {
            try {
                TerminalSize size = screen.getTerminalSize();
                int width = size.getColumns();
                int height = size.getRows();

                if (width < 20 || height < 10) {
                    // Terminal too small to render full UI
                    TextGraphics tg = screen.newTextGraphics();
                    tg.fill(' ');
                    tg.putString(1, 1, "Terminal too small!", SGR.BOLD);
                    screen.refresh();
                    return;
                }

                TextGraphics tg = screen.newTextGraphics();
                tg.fill(' ');

                // 1. Render Header Bar (Row 0)
                renderHeader(tg, width);

                // 2. Calculate dimensions for Message Box & Input Box
                int msgBoxY = 1;
                int msgBoxHeight = height - 5; // Leaves room for hints and input box
                int hintY = height - 4;
                int inputBoxY = height - 3;
                int inputBoxHeight = 3;

                // 3. Render Message History Box
                renderMessages(tg, 0, msgBoxY, width, msgBoxHeight);

                // 4. Render Shortcut Hints
                renderHints(tg, 0, hintY, width);

                // 5. Render Input Box
                renderInputBox(tg, 0, inputBoxY, width, inputBoxHeight);

                // Position hardware cursor inside input box
                int cursorScreenX = 4 + cursorIdx;
                int cursorScreenY = inputBoxY + 1;
                if (cursorScreenX < width - 2) {
                    screen.setCursorPosition(new TerminalPosition(cursorScreenX, cursorScreenY));
                } else {
                    screen.setCursorPosition(new TerminalPosition(width - 3, cursorScreenY));
                }

                screen.refresh();

            } catch (IOException e) {
                // Ignore transient draw errors on resize
            }
        }
    }

    private void renderHeader(TextGraphics tg, int width) {
        tg.setBackgroundColor(COLOR_HEADER_BG);
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        for (int x = 0; x < width; x++) {
            tg.setCharacter(x, 0, ' ');
        }

        tg.putString(1, 0, "💬 TERMINAL MESSENGER", SGR.BOLD);

        // Status indicator
        String statusText = connected ? "● ONLINE" : "○ OFFLINE";
        TextColor statusColor = connected ? TextColor.ANSI.GREEN_BRIGHT : TextColor.ANSI.RED_BRIGHT;
        tg.setForegroundColor(statusColor);
        tg.putString(24, 0, statusText, SGR.BOLD);

        // Peer host & port
        tg.setForegroundColor(TextColor.ANSI.WHITE);
        String serverInfo = "[" + host + ":" + port + "]";
        tg.putString(35, 0, serverInfo);

        // Current Nickname and Clock
        String rightText = "Nick: " + nickname + "  " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " ";
        int rightX = Math.max(width - rightText.length() - 1, 55);
        if (rightX + rightText.length() <= width) {
            tg.putString(rightX, 0, rightText);
        }

        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    private void renderMessages(TextGraphics tg, int x, int y, int width, int height) {
        // Draw Box Border
        drawBox(tg, x, y, width, height, " Message History ", COLOR_BORDER);

        int contentWidth = width - 4;
        int visibleRows = height - 2;

        if (contentWidth <= 0 || visibleRows <= 0) {
            return;
        }

        // Format and wrap messages
        List<FormattedLine> lines = new ArrayList<>();
        for (ChatMessage msg : messages) {
            String prefix = "[" + msg.timestamp + "] [" + msg.sender + "]: ";
            int maxContentWidth = Math.max(10, contentWidth - prefix.length());

            List<String> wrapped = wrapText(msg.text, maxContentWidth);
            for (int i = 0; i < wrapped.size(); i++) {
                if (i == 0) {
                    lines.add(new FormattedLine(msg.timestamp, "[" + msg.sender + "]: ", wrapped.get(i), msg.type, false));
                } else {
                    lines.add(new FormattedLine(null, null, wrapped.get(i), msg.type, true));
                }
            }
        }

        int totalLines = lines.size();

        // Clamp scroll offset
        int maxScroll = Math.max(0, totalLines - visibleRows);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        // Show scroll indicator if scrolled up
        if (scrollOffset > 0) {
            tg.setForegroundColor(TextColor.ANSI.YELLOW_BRIGHT);
            String scrollNotice = " [▲ Scrolled +" + scrollOffset + " (PgDn to return)] ";
            int noticeX = Math.max(x + 2, width - scrollNotice.length() - 3);
            tg.putString(noticeX, y, scrollNotice, SGR.BOLD);
        }

        // Calculate slice to render
        int startIndex = Math.max(0, totalLines - visibleRows - scrollOffset);
        int endIndex = Math.min(totalLines, startIndex + visibleRows);

        int drawY = y + 1;
        for (int i = startIndex; i < endIndex; i++) {
            FormattedLine line = lines.get(i);
            int drawX = x + 2;

            if (!line.isContinuation) {
                // Render Timestamp
                tg.setForegroundColor(COLOR_TIMESTAMP);
                tg.putString(drawX, drawY, "[" + line.timestamp + "] ");
                drawX += 11;

                // Render Sender Badge
                switch (line.type) {
                    case SELF -> {
                        tg.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
                        tg.putString(drawX, drawY, line.senderPrefix, SGR.BOLD);
                    }
                    case PEER -> {
                        tg.setForegroundColor(TextColor.ANSI.CYAN_BRIGHT);
                        tg.putString(drawX, drawY, line.senderPrefix, SGR.BOLD);
                    }
                    case SYSTEM -> {
                        tg.setForegroundColor(TextColor.ANSI.YELLOW_BRIGHT);
                        tg.putString(drawX, drawY, "⚡ " + line.senderPrefix, SGR.BOLD);
                        drawX += 2;
                    }
                    case ERROR -> {
                        tg.setForegroundColor(TextColor.ANSI.RED_BRIGHT);
                        tg.putString(drawX, drawY, "✖ " + line.senderPrefix, SGR.BOLD);
                        drawX += 2;
                    }
                }
                drawX += line.senderPrefix.length();

                // Render Content
                tg.setForegroundColor(getTextColorForType(line.type));
                tg.putString(drawX, drawY, line.content);

            } else {
                // Continuation line (indented)
                drawX += 11 + (line.senderPrefix != null ? line.senderPrefix.length() : 8);
                tg.setForegroundColor(getTextColorForType(line.type));
                tg.putString(drawX, drawY, line.content);
            }

            drawY++;
        }
    }

    private TextColor getTextColorForType(MessageType type) {
        return switch (type) {
            case SELF -> TextColor.ANSI.WHITE_BRIGHT;
            case PEER -> TextColor.ANSI.WHITE;
            case SYSTEM -> TextColor.ANSI.YELLOW;
            case ERROR -> TextColor.ANSI.RED;
        };
    }

    private void renderHints(TextGraphics tg, int x, int y, int width) {
        tg.setForegroundColor(COLOR_HINT);
        String hints = " [Enter] Send  •  [PgUp/Dn] Scroll  •  [/help] Commands  •  [F10] Quit";
        tg.putString(x + 1, y, hints);
    }

    private void renderInputBox(TextGraphics tg, int x, int y, int width, int height) {
        String title = " Message (" + nickname + ") ";
        drawBox(tg, x, y, width, height, title, COLOR_INPUT_BORDER);

        // Prompt
        tg.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        tg.putString(x + 2, y + 1, "❯ ", SGR.BOLD);

        // Input text
        tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        String text = inputBuffer.toString();
        int maxLen = width - 6;
        if (text.length() > maxLen) {
            text = text.substring(text.length() - maxLen);
        }
        tg.putString(x + 4, y + 1, text);
    }

    private void drawBox(TextGraphics tg, int x, int y, int width, int height, String title, TextColor borderColor) {
        tg.setForegroundColor(borderColor);

        // Top border with rounded corners
        tg.putString(x, y, "╭");
        for (int i = 1; i < width - 1; i++) {
            tg.putString(x + i, y, "─");
        }
        tg.putString(x + width - 1, y, "╮");

        // Optional title
        if (title != null && title.length() < width - 4) {
            tg.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
            tg.putString(x + 2, y, title, SGR.BOLD);
            tg.setForegroundColor(borderColor);
        }

        // Side borders
        for (int j = 1; j < height - 1; j++) {
            tg.putString(x, y + j, "│");
            tg.putString(x + width - 1, y + j, "│");
        }

        // Bottom border
        tg.putString(x, y + height - 1, "╰");
        for (int i = 1; i < width - 1; i++) {
            tg.putString(x + i, y + height - 1, "─");
        }
        tg.putString(x + width - 1, y + height - 1, "╯");
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        if (maxWidth <= 0) {
            maxWidth = 40;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (word.length() > maxWidth) {
                // Break overly long single words
                if (!currentLine.isEmpty()) {
                    result.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                int idx = 0;
                while (idx < word.length()) {
                    int chunk = Math.min(maxWidth, word.length() - idx);
                    result.add(word.substring(idx, idx + chunk));
                    idx += chunk;
                }
                continue;
            }

            if (currentLine.length() + word.length() + (currentLine.isEmpty() ? 0 : 1) <= maxWidth) {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                result.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            result.add(currentLine.toString());
        }

        return result;
    }

    private void cleanup() {
        running = false;
        try {
            if (screen != null) {
                screen.stopScreen();
            }
        } catch (Exception ignored) {}

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {}
    }
}
