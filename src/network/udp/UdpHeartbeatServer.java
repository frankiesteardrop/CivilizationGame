package network.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP heartbeat server — listens for PING packets from clients and replies with PONG.
 *
 * <p>Protocol (plain text over UDP):
 * <pre>
 *   Client → Server:  "PING:{clientId}"
 *   Server → Client:  "PONG:{clientId}"
 * </pre>
 *
 * <p>The server tracks the last ping time per clientId.
 * {@link #isClientAlive(String)} can be used to detect disconnected clients.
 */
public class UdpHeartbeatServer implements Runnable {

    public static final int UDP_PORT      = 8081;
    public static final int BUFFER_SIZE   = 256;
    public static final long TIMEOUT_MS   = 15_000L; // 15 seconds without ping = dead

    private final ConcurrentHashMap<String, Long> lastPingTime = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    // ─── Lifecycle ────────────────────────────────────────────────────────────

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
            socket.setSoTimeout(2000); // 2-second timeout for clean shutdown
            byte[] buffer = new byte[BUFFER_SIZE];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                    handlePacket(socket, packet);
                } catch (java.net.SocketTimeoutException ignored) {
                    // Normal: periodic wakeup to check running flag
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [UDP] Heartbeat server error: " + e.getMessage());
        }
    }

    // ─── Packet Handling ──────────────────────────────────────────────────────

    private void handlePacket(DatagramSocket socket, DatagramPacket packet) {
        String message = new String(packet.getData(), 0, packet.getLength()).trim();

        if (message.startsWith("PING:")) {
            String clientId = message.substring(5);
            lastPingTime.put(clientId, System.currentTimeMillis());

            // Reply with PONG to the same client address + port
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

    // ─── Status API ───────────────────────────────────────────────────────────

    /**
     * Returns true if the client has pinged within the last {@value TIMEOUT_MS} ms.
     */
    public boolean isClientAlive(String clientId) {
        Long lastTime = lastPingTime.get(clientId);
        if (lastTime == null) return false;
        return (System.currentTimeMillis() - lastTime) < TIMEOUT_MS;
    }

    /** Removes tracking data for a client that has disconnected via TCP. */
    public void removeClient(String clientId) {
        lastPingTime.remove(clientId);
    }

    /** Returns a snapshot of all tracked client IDs and their last-ping times. */
    public Map<String, Long> getLastPingTimes() {
        return java.util.Collections.unmodifiableMap(lastPingTime);
    }
}