package network.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpHeartbeatClient {

    private static final int    PING_INTERVAL_MS = 5_000;
    private static final int    PONG_TIMEOUT_MS  = 3_000;
    private static final int    BUFFER_SIZE      = 256;

    private final String serverHost;
    private final int    serverUdpPort;

    private String clientId;

    private volatile boolean serverAlive = true;
    private volatile boolean running     = false;

    public UdpHeartbeatClient(String serverHost, String clientId) {
        this.serverHost    = serverHost;
        this.serverUdpPort = UdpHeartbeatServer.UDP_PORT;
        this.clientId      = clientId;
    }

    public void updateClientId(String newClientId) {
        this.clientId = newClientId;
        System.out.println("🔄 [UDP] Heartbeat Client ID updated to: " + newClientId);
    }

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

    public boolean isServerAlive() {
        return serverAlive;
    }

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
            String ping = "PING:" + clientId;
            byte[] pingBytes = ping.getBytes();
            DatagramPacket pingPacket = new DatagramPacket(
                    pingBytes, pingBytes.length, serverAddress, serverUdpPort);
            socket.send(pingPacket);

            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket pongPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(pongPacket);

            String response = new String(pongPacket.getData(), 0, pongPacket.getLength()).trim();
            return response.equals("PONG:" + clientId);

        } catch (java.net.SocketTimeoutException e) {
            return false;
        } catch (Exception e) {
            System.err.println("[UDP] Error during ping: " + e.getMessage());
            return false;
        }
    }
}