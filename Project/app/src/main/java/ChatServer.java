import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer
{
	public static void main(String[] args)
	{
		int port = 5000;

		try(ServerSocket serverSocket = new ServerSocket(port))
		{
			System.out.println("Server is listening on port: " + port);

			try(Socket socket = ServerSocket.accept())
			{
				System.out.println("Client connected successfullly!");

				BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				String clientMessage;
				while((clientMessage = reader.readLine()) != null)
				{
					System.out.println("Client says: " + clientMessage);
					if("Over".equalsIgnoreCare(clientMessage))
					{
						System.out.println("Termination command received. Closing connection");
						break;
					}
				}
			}
		} catch (IOException e)
		{
			System.err.println("Server exception: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
}
