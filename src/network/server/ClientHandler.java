package network.server;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

/**
 * مدیریت اتصال یک کلاینت در thread اختصاصی.
 * هر پیام JSON دریافتی فوری به GameServer.routeMessage() ارسال می‌شود؛
 * این کلاس هیچ منطق بازی یا لابی ندارد (Single Responsibility).
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private final String clientId;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket   = socket;
        this.server   = server;
        this.clientId = UUID.randomUUID().toString(); // شناسه منحصربه‌فرد این اتصال
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            server.addClient(clientId, this);

            String incomingJson;
            while ((incomingJson = in.readLine()) != null) {
                // هر پیام JSON دریافتی به router مرکزی سرور تحویل داده می‌شود
                server.routeMessage(clientId, incomingJson);
            }
        } catch (IOException e) {
            System.out.println("[Server] Connection dropped for " + clientId);
        } finally {
            server.removeClient(clientId);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void sendMessage(String jsonMessage) {
        if (out != null) {
            out.println(jsonMessage);
        }
    }

    public String getClientId() { return clientId; }
}