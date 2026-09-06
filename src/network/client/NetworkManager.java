package network.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.udp.UdpHeartbeatClient;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.Socket;

public class NetworkManager {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;

    private ServerMessageHandler messageHandler;
    private UdpHeartbeatClient udpHeartbeat;

    private String myClientId = "unknown";
    private String jwtToken = null; // نگهداری توکن دریافتی

    public void setMessageHandler(ServerMessageHandler handler) {
        this.messageHandler = handler;
    }

    public void setMyClientId(String clientId) {
        this.myClientId = clientId;
    }

    public void setJwtToken(String token) {
        this.jwtToken = token;
    }

    public void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;

            Thread listenerThread = new Thread(new ListenerTask());
            listenerThread.setDaemon(true);
            listenerThread.setName("network-listener");
            listenerThread.start();

            udpHeartbeat = new UdpHeartbeatClient(host, myClientId);
            udpHeartbeat.start();

            System.out.println("✅ [Client] Connected to server at " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("❌ [Client] Connection failed: " + e.getMessage());
        }
    }

    public void sendRequest(String jsonMessage) {
        if (isConnected && out != null) {
            // تزریق توکن به ریشه JSON قبل از ارسال به سرور
            if (jwtToken != null) {
                try {
                    JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
                    obj.addProperty("token", jwtToken);
                    jsonMessage = obj.toString();
                } catch (Exception e) {
                    System.err.println("[Client] Failed to attach token: " + e.getMessage());
                }
            }
            final String finalMsg = jsonMessage;
            new Thread(() -> out.println(finalMsg)).start();
        }
    }

    public boolean isConnected() { return isConnected; }

    public boolean isServerAlive() {
        return udpHeartbeat == null || udpHeartbeat.isServerAlive();
    }

    public void disconnect() {
        isConnected = false;
        if (udpHeartbeat != null) udpHeartbeat.stop();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private class ListenerTask implements Runnable {
        @Override
        public void run() {
            try {
                String incomingJson;
                while ((incomingJson = in.readLine()) != null) {
                    final String msg  = incomingJson;
                    final String type = extractMessageType(msg);

                    // استخراج و ذخیره توکن از پیام تخصیص شناسه
                    if ("PLAYER_ID_ASSIGNED".equals(type)) {
                        try {
                            JsonObject obj = JsonParser.parseString(msg).getAsJsonObject();
                            if (obj.has("jwtToken")) {
                                setJwtToken(obj.get("jwtToken").getAsString());
                            }
                        } catch(Exception ignored){}
                    }

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