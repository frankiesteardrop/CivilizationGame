package network.server;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private final String clientId;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket = socket;
        this.server = server;
        this.clientId = UUID.randomUUID().toString(); // شناسه موقت تا زمان ورود کامل
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            server.addClient(clientId, this);

            String incomingJson;
            while ((incomingJson = in.readLine()) != null) {
                // TODO: در گام‌های بعدی، پیام JSON به آبجکت تبدیل شده و اعتبارسنجی می‌شود
                System.out.println("[Server] Received from " + clientId + ": " + incomingJson);
            }
        } catch (IOException e) {
            System.out.println("[Server] Connection dropped for " + clientId);
        } finally {
            server.removeClient(clientId);
            try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public void sendMessage(String jsonMessage) {
        if (out != null) {
            out.println(jsonMessage);
        }
    }

    public String getClientId() { return clientId; }
}