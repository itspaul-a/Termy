import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {
    public static void main(String[] args) {
        int port = 5000; // Port numbers above 1024 are safe from system restrictions
        
        // Try-with-resources automatically closes sockets and streams when finished
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);
            
            // This method blocks execution until a client establishes a connection
            try (Socket socket = serverSocket.accept()) {
                System.out.println("Client connected successfully!");
                
                // Set up stream to read data from the client
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                String clientMessage;
                while ((clientMessage = reader.readLine()) != null) {
                    System.out.println("Client says: " + clientMessage);
                    if ("Over".equalsIgnoreCase(clientMessage)) {
                        System.out.println("Termination command received. Closing connection.");
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

