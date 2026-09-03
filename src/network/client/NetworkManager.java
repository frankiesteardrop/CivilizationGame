package network.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.Socket;

/**
 * Manages the TCP connection to the game server.
 *
 * <p>Architecture:
 * <ul>
 *   <li>Sending: UI actions are sent from EDT via controller; writing to the socket happens
 *       on a short-lived thread so the EDT is never blocked.</li>
 *   <li>Receiving: a dedicated daemon ListenerTask thread reads from the socket continuously.
 *       When a message arrives it extracts the "type" field and delegates to the registered
 *       {@link ServerMessageHandler} via {@code SwingUtilities.invokeLater} so all UI
 *       updates happen safely on the EDT.</li>
 * </ul>
 */
public class NetworkManager {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;

    /**
     * Dispatcher registered by the client-side coordinator.
     * Must be set before or immediately after {@link #connect} is called.
     */
    private ServerMessageHandler messageHandler;

    // ─── Configuration ────────────────────────────────────────────────────────

    public void setMessageHandler(ServerMessageHandler handler) {
        this.messageHandler = handler;
    }

    // ─── Connection ───────────────────────────────────────────────────────────

    public void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;

            Thread listenerThread = new Thread(new ListenerTask());
            listenerThread.setDaemon(true);       // پایان خودکار با بستن برنامه
            listenerThread.setName("network-listener");
            listenerThread.start();

            System.out.println("✅ [Client] Connected to server at " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("❌ [Client] Connection failed: " + e.getMessage());
        }
    }

    /**
     * ارسال پیام روی thread مجزا تا در صورت پر شدن بافر، EDT قفل نشود.
     */
    public void sendRequest(String jsonMessage) {
        if (isConnected && out != null) {
            new Thread(() -> out.println(jsonMessage)).start();
        }
    }

    public boolean isConnected() { return isConnected; }

    // ─── Listener Thread (کاملاً مجزا از EDT) ─────────────────────────────────

    /**
     * نخ شنونده دائمی.
     * پیام دریافتی را parse کرده و از طریق invokeLater به EDT و handler تحویل می‌دهد.
     * با این طراحی UI هیچ‌وقت فریز نمی‌شود و هیچ پیامی از سرور از دست نمی‌رود.
     */
    private class ListenerTask implements Runnable {
        @Override
        public void run() {
            try {
                String incomingJson;
                while ((incomingJson = in.readLine()) != null) {
                    final String msg  = incomingJson;
                    final String type = extractMessageType(msg);

                    // انتقال ایمن به نخ گرافیک (EDT)
                    SwingUtilities.invokeLater(() -> {
                        if (messageHandler != null) {
                            messageHandler.onMessage(type, msg);
                        } else {
                            System.out.println("[Client] No handler registered. Dropped type: " + type);
                        }
                    });
                }
            } catch (IOException e) {
                isConnected = false;
                System.out.println("⚠️ [Client] Disconnected from server.");
                // اطلاع‌رسانی قطع اتصال به handler تا UI بتواند پنجره خطا نمایش دهد
                SwingUtilities.invokeLater(() -> {
                    if (messageHandler != null) {
                        messageHandler.onMessage("DISCONNECTED", "{}");
                    }
                });
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * فیلد "type" را از JSON خوانده و برمی‌گرداند.
     * در صورت خطا "UNKNOWN" برمی‌گردد تا handler بتواند gracefully آن را مدیریت کند.
     */
    private String extractMessageType(String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has("type")) {
                return obj.get("type").getAsString();
            }
        } catch (Exception e) {
            System.err.println("[Client] Failed to parse message type. Raw: " + json);
        }
        return "UNKNOWN";
    }
}