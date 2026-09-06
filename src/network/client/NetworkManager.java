package network.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.udp.UdpHeartbeatClient;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NetworkManager {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;

    private ServerMessageHandler messageHandler;
    private UdpHeartbeatClient udpHeartbeat;

    private String myClientId = "unknown";
    private String jwtToken = null;

    // 🔴 حفظ اصلاحیه‌ی M-12 (صف برای جلوگیری از Reordering)
    private final BlockingQueue<String> sendQueue = new LinkedBlockingQueue<>();
    private Thread senderThread;

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

            // 🔴 اجرای Sender اختصاصی برای صف
            senderThread = new Thread(() -> {
                try {
                    while (isConnected || !sendQueue.isEmpty()) {
                        String msg = sendQueue.poll(500, TimeUnit.MILLISECONDS);
                        if (msg != null && out != null) {
                            out.println(msg);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "network-sender");
            senderThread.setDaemon(true);
            senderThread.start();

            udpHeartbeat = new UdpHeartbeatClient(host, myClientId);
            udpHeartbeat.start();

            System.out.println("✅ [Client] Connected to server at " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("❌ [Client] Connection failed: " + e.getMessage());
        }
    }

    public void sendRequest(String jsonMessage) {
        if (!isConnected) return;
        if (jwtToken != null) {
            try {
                JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
                obj.addProperty("token", jwtToken);
                jsonMessage = obj.toString();
            } catch (Exception e) {
                System.err.println("[Client] Failed to attach token: " + e.getMessage());
            }
        }
        // ارسال پیام به صف (بدون ساخت ترد جدید)
        sendQueue.offer(jsonMessage);
    }

    public boolean isConnected() { return isConnected; }

    public boolean isServerAlive() {
        return udpHeartbeat == null || udpHeartbeat.isServerAlive();
    }

    public void disconnect() {
        isConnected = false;
        if (senderThread != null) senderThread.interrupt();
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

                    if ("PLAYER_ID_ASSIGNED".equals(type)) {
                        try {
                            JsonObject obj = JsonParser.parseString(msg).getAsJsonObject();
                            if (obj.has("jwtToken")) {
                                setJwtToken(obj.get("jwtToken").getAsString());
                            }
                            // 🔴 FIX M-16: همگام‌سازی UDP با UUID قطعی سرور
                            if (obj.has("clientId")) {
                                String realUUID = obj.get("clientId").getAsString();
                                setMyClientId(realUUID);
                                if (udpHeartbeat != null) {
                                    udpHeartbeat.updateClientId(realUUID);
                                }
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
                if (senderThread != null) senderThread.interrupt();
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