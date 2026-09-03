package network.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * UDP heartbeat client — sends periodic PING packets to the server and
 * listens for PONG replies to confirm the connection is still alive.
 *
 * <p>Must be started after a TCP connection is established so that
 * {@code clientId} (received from the server) is available.
 *
 * <p>Usage:
 * <pre>
 *   UdpHeartbeatClient hb = new UdpHeartbeatClient("192.168.1.1", clientId);
 *   hb.start();
 *   // later...
 *   if (!hb.isServerAlive()) { ... handle disconnect ... }
 * </pre>
 */
public class UdpHeartbeatClient {

    private static final int    PING_INTERVAL_MS = 5_000;  // send a ping every 5 s
    private static final int    PONG_TIMEOUT_MS  = 3_000;  // wait up to 3 s for pong
    private static final int    BUFFER_SIZE      = 256;

    private final String serverHost;
    private final int    serverUdpPort;
    private final String clientId;

    private volatile boolean serverAlive = true;
    private volatile boolean running     = false;

    public UdpHeartbeatClient(String serverHost, String clientId) {
        this.serverHost    = serverHost;
        this.serverUdpPort = UdpHeartbeatServer.UDP_PORT;
        this.clientId      = clientId;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    public void start() {
        Thread pingThread = new Thread(this::pingLoop, "udp-heartbeat-client");
        pingThread.setDaemon(true);
        pingThread.start();
        System.out.println("✅ [UDP] Heartbeat client started. Pinging " + serverHost
                + ":" + serverUdpPort + " every " + PING_INTERVAL_MS + " ms");
    }

    public void stop() {
        running = false;
    }

    /** Returns false if the server has not responded to the last ping within the timeout. */
    public boolean isServerAlive() {
        return serverAlive;
    }

    // ─── Ping Loop ────────────────────────────────────────────────────────────

    private void pingLoop() {
        running = true;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(PONG_TIMEOUT_MS);
            InetAddress serverAddress = InetAddress.getByName(serverHost);

            while (running) {
                boolean pongReceived = sendPingAndWaitForPong(socket, serverAddress);
                if (!pongReceived) {
                    serverAlive = false;
                    System.err.println("⚠️ [UDP] Server did not respond to PING. Connection may be lost.");
                } else {
                    serverAlive = true;
                }

                try {
                    Thread.sleep(PING_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [UDP] Heartbeat client error: " + e.getMessage());
            serverAlive = false;
        }
    }

    private boolean sendPingAndWaitForPong(DatagramSocket socket, InetAddress serverAddress) {
        try {
            // Send PING
            String ping = "PING:" + clientId;
            byte[] pingBytes = ping.getBytes();
            DatagramPacket pingPacket = new DatagramPacket(
                    pingBytes, pingBytes.length, serverAddress, serverUdpPort);
            socket.send(pingPacket);

            // Wait for PONG
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket pongPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(pongPacket); // blocks until PONG_TIMEOUT_MS

            String response = new String(pongPacket.getData(), 0, pongPacket.getLength()).trim();
            return response.equals("PONG:" + clientId);

        } catch (java.net.SocketTimeoutException e) {
            return false; // timed out waiting for PONG
        } catch (Exception e) {
            System.err.println("[UDP] Error during ping: " + e.getMessage());
            return false;
        }
    }
}