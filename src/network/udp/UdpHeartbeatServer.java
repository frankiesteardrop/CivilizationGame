package network.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UdpHeartbeatServer implements Runnable {

    public static final int UDP_PORT      = 8081;
    public static final int BUFFER_SIZE   = 256;
    public static final long TIMEOUT_MS   = 15_000L;

    private final ConcurrentHashMap<String, Long> lastPingTime = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    public void start() {
        Thread t = new Thread(this, "udp-heartbeat-server");
        t.setDaemon(true);
        t.start();
        System.out.println("✅ [UDP] Heartbeat server started on port " + UDP_PORT);
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        running = true;
        try (DatagramSocket socket = new DatagramSocket(UDP_PORT)) {
            socket.setSoTimeout(2000);
            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    handlePacket(socket, packet);
                } catch (java.net.SocketTimeoutException ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [UDP] Heartbeat server error: " + e.getMessage());
        }
    }

    private void handlePacket(DatagramSocket socket, DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength()).trim();

        if (message.startsWith("PING:")) {
            String clientId = message.substring(5);
            lastPingTime.put(clientId, System.currentTimeMillis());

            String pong = "PONG:" + clientId;
            byte[] pongBytes = pong.getBytes();
            DatagramPacket response = new DatagramPacket(
                    pongBytes, pongBytes.length,
                    packet.getAddress(), packet.getPort());
            try {
                socket.send(response);
            } catch (Exception e) {
                System.err.println("[UDP] Failed to send PONG to " + clientId);
            }
        }
    }

    public boolean isClientAlive(String clientId) {
        Long lastTime = lastPingTime.get(clientId);
        if (lastTime == null) return false;
        return (System.currentTimeMillis() - lastTime) < TIMEOUT_MS;
    }

    public void removeClient(String clientId) {
        lastPingTime.remove(clientId);
    }

    public Map<String, Long> getLastPingTimes() {
        return java.util.Collections.unmodifiableMap(lastPingTime);
    }
}