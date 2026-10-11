import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

public class ChatServer {
    public static void main(String[] args) {
        int port = 5000; // Port numbers above 1024 are safe from system restrictions
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        String dbPath = (args.length > 1) ? args[1] : "termy.db";

        try (MessageStore messageStore = new MessageStore(dbPath);
             ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("Message store active: " + dbPath + " (" + messageStore.getMessageCount() + " messages loaded).");
            System.out.println("Server is listening on port " + port + ". Waiting for client to connect...");

            try (Socket socket = serverSocket.accept()) {
                System.out.println("Client connected successfully! Start chatting (type 'exit' to quit):");

                // Display recent room history on the server console if available
                List<StoredMessage> history = messageStore.getRecentRoomMessages("general", 5);
                if (!history.isEmpty()) {
                    System.out.println("--- Recent History (last " + history.size() + ") ---");
                    for (StoredMessage msg : history) {
                        System.out.println("[" + msg.formattedTime() + "] [" + msg.sender() + "]: " + msg.content());
                    }
                    System.out.println("------------------------------------");
                }

                BufferedReader networkReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter networkWriter = new PrintWriter(socket.getOutputStream(), true);

                // Background thread: continuously listens for incoming messages from the client
                Thread receiveThread = new Thread(() -> {
                    try {
                        String clientMessage;
                        while ((clientMessage = networkReader.readLine()) != null) {
                            if ("exit".equalsIgnoreCase(clientMessage)) {
                                System.out.println("\nClient ended the chat session. Press Enter to exit.");
                                break;
                            }

                            // Persist client message to SQLite
                            try {
                                messageStore.saveMessage("Client", null, "general", clientMessage);
                            } catch (SQLException e) {
                                System.err.println("Failed to persist client message: " + e.getMessage());
                            }

                            System.out.println("\n[Client]: " + clientMessage);
                            System.out.print("> ");
                        }
                    } catch (IOException e) {
                        System.out.println("\nConnection closed by client. Press Enter to exit.");
                    }
                });
                receiveThread.setDaemon(true);
                receiveThread.start();

                // Main thread: reads keyboard input and sends to client
                Scanner consoleScanner = new Scanner(System.in);
                System.out.print("> ");
                while (consoleScanner.hasNextLine()) {
                    String userMessage = consoleScanner.nextLine();
                    networkWriter.println(userMessage);
                    if ("exit".equalsIgnoreCase(userMessage)) {
                        System.out.println("Closing server connection...");
                        break;
                    }

                    // Persist server message to SQLite
                    try {
                        messageStore.saveMessage("Server", null, "general", userMessage);
                    } catch (SQLException e) {
                        System.err.println("Failed to persist server message: " + e.getMessage());
                    }

                    System.out.print("> ");
                }
            }
        } catch (IOException | SQLException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
