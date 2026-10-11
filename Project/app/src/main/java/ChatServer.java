import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class ChatServer {
    public static void main(String[] args) {
        int port = 5000; // Port numbers above 1024 are safe from system restrictions

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port + ". Waiting for client to connect...");

            try (Socket socket = serverSocket.accept()) {
                System.out.println("Client connected successfully! Start chatting (type 'exit' to quit):");

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
                    System.out.print("> ");
                }
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
