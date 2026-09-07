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
    private volatile boolean isRunning = true;

    public ClientHandler(Socket socket, GameServer server) {
        this.socket   = socket;
        this.server   = server;
        this.clientId = UUID.randomUUID().toString();
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            server.addClient(clientId, this);

            String incomingJson;
            while (isRunning && (incomingJson = in.readLine()) != null) {
                server.routeMessage(clientId, incomingJson);
            }
        } catch (IOException e) {
            System.out.println("[Server] Connection dropped for " + clientId);
        } finally {
            close();
            server.removeClient(clientId);
        }
    }

    public void sendMessage(String jsonMessage) {
        if (out != null && isRunning) {
            out.println(jsonMessage);
            if (out.checkError()) {
                System.out.println("🚨 [Server] Write failed, forcing disconnect for " + clientId);
                close();
                server.removeClient(clientId);
            }
        }
    }

    public void close() {
        isRunning = false;
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    public String getClientId() { return clientId; }
}