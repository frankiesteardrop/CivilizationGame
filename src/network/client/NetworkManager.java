package network.client;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.Socket;

public class NetworkManager {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;

    public void connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;

            Thread listenerThread = new Thread(new ListenerTask());
            listenerThread.setDaemon(true); // پایان خودکار با بستن برنامه
            listenerThread.start();

            System.out.println("✅ [Client] Connected to server at " + host + ":" + port);
        } catch (IOException e) {
            System.err.println("❌ [Client] Connection failed: " + e.getMessage());
        }
    }

    // ارسال پیام در ترد مجزا تا در صورت پر شدن بافر، UI قفل نشود
    public void sendRequest(String jsonMessage) {
        if (isConnected && out != null) {
            new Thread(() -> out.println(jsonMessage)).start();
        }
    }

    // نخ شنونده: کاملاً مجزا از EDT (رابط کاربری)
    private class ListenerTask implements Runnable {
        @Override
        public void run() {
            try {
                String incomingJson;
                while ((incomingJson = in.readLine()) != null) {
                    final String msg = incomingJson;

                    // انتقال ایمن دیتای شبکه به نخ گرافیک (EDT)
                    SwingUtilities.invokeLater(() -> {
                        // TODO: در گام بعد پیام به EventDispatcher داده می‌شود
                        System.out.println("[Client] UI received: " + msg);
                    });
                }
            } catch (IOException e) {
                isConnected = false;
                System.out.println("⚠️ [Client] Disconnected from server.");
                SwingUtilities.invokeLater(() -> {
                    // TODO: نمایش پنجره خطای قطع اتصال به بازیکن
                });
            }
        }
    }
}