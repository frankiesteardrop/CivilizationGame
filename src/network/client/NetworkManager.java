package network.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.udp.UdpHeartbeatClient;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.Socket;

/**
 * Manages the TCP connection to the game server plus a UDP heartbeat client.
 *
 * <p>Sending: UI actions are dispatched to the server on a short-lived thread,
 * so the EDT is never blocked by socket I/O.
 *
 * <p>Receiving: a dedicated daemon {@link ListenerTask} thread reads from the
 * TCP socket continuously. Each arriving message is parsed for its "type" field
 * and handed to the registered {@link ServerMessageHandler} via
 * {@code SwingUtilities.invokeLater} (EDT-safe delivery).
 *
 * <p>Heartbeat (B6): after {@link #connect} succeeds, a {@link UdpHeartbeatClient}
 * is started. The server uses the UDP pings to detect if this client is still alive.
 */
public class NetworkManager {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;

    /** Dispatches received messages to the appropriate client-side controller. */
    private ServerMessageHandler messageHandler;

    /** UDP heartbeat client — started after TCP connection succeeds. */
    private UdpHeartbeatClient udpHeartbeat;

    /** The client's own TCP-assigned ID (set after JOIN_LOBBY acknowledgement). */
    private String myClientId = "unknown";

    // ─── Configuration ────────────────────────────────────────────────────────

    public void setMessageHandler(ServerMessageHandler handler) {
        this.messageHandler = handler;
    }

    public void setMyClientId(String clientId) {
        this.myClientId = clientId;
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    public void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;

            // Start TCP listener thread
            Thread listenerThread = new Thread(new ListenerTask());
            listenerThread.setDaemon(true);
            listenerThread.setName("network-listener");
            listenerThread.start();

            // Start UDP heartbeat client (B6)
            udpHeartbeat = new UdpHeartbeatClient(host, myClientId);
            udpHeartbeat.start();

            System.out.println("✅ [Client] Connected to server at " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("❌ [Client] Connection failed: " + e.getMessage());
        }
    }

    /**
     * Sends a JSON message to the server on a short-lived thread so that
     * EDT is never blocked by socket backpressure.
     */
    public void sendRequest(String jsonMessage) {
        if (isConnected && out != null) {
            new Thread(() -> out.println(jsonMessage)).start();
        }
    }

    public boolean isConnected() { return isConnected; }

    /** Returns true if the server has recently responded to UDP pings. */
    public boolean isServerAlive() {
        return udpHeartbeat == null || udpHeartbeat.isServerAlive();
    }

    public void disconnect() {
        isConnected = false;
        if (udpHeartbeat != null) udpHeartbeat.stop();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    // ─── Listener Thread ──────────────────────────────────────────────────────

    /**
     * Daemon thread that blocks on the TCP socket.
     * Wakes up for each arriving line, extracts the "type" field, and
     * delivers the message to {@link #messageHandler} on the EDT.
     */
    private class ListenerTask implements Runnable {
        @Override
        public void run() {
            try {
                String incomingJson;
                while ((incomingJson = in.readLine()) != null) {
                    final String msg  = incomingJson;
                    final String type = extractMessageType(msg);

                    SwingUtilities.invokeLater(() -> {
                        if (messageHandler != null) {
                            messageHandler.onMessage(type, msg);
                        } else {
                            System.out.println("[Client] No handler registered. Dropped: " + type);
                        }
                    });
                }
            } catch (IOException e) {
                isConnected = false;
                if (udpHeartbeat != null) udpHeartbeat.stop();
                System.out.println("⚠️ [Client] Disconnected from server.");
                SwingUtilities.invokeLater(() -> {
                    if (messageHandler != null) {
                        messageHandler.onMessage("DISCONNECTED", "{}");
                    }
                });
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String extractMessageType(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("type")) return obj.get("type").getAsString();
        } catch (Exception e) {
            System.err.println("[Client] Failed to parse message type. Raw: " + json);
        }
        return "UNKNOWN";
    }
}