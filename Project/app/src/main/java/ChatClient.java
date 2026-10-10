import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {
    public static void main(String[] args) {
        String hostname = "localhost"; // IP address or name of the server host
        int port = 5000;
        
        try (Socket socket = new Socket(hostname, port)) {
            System.out.println("Connected to the server!");
            
            // Set up stream to send data to the server
            // 'true' enabling auto-flush ensures data is sent immediately
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            
            // Send payload data
            writer.println("Hello from the client application!");
            writer.println("Testing the connection stream.");
            writer.println("Over"); // Send the termination signal expected by our server
            
        } catch (IOException e) {
            System.err.println("Client exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

