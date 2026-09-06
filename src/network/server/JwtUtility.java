package network.server;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class JwtUtility {

    // کلید مخفی سرور (باید در محیط واقعی در فایل .env ذخیره شود)
    private static final String SECRET = "Sharif_AP_Super_Secret_Key_2026_!@#";

    public static String generateToken(String username, String clientId) {
        try {
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long exp = System.currentTimeMillis() + (1000 * 60 * 60 * 24); // 24 ساعت اعتبار
            String payload = String.format("{\"sub\":\"%s\",\"name\":\"%s\",\"exp\":%d}", clientId, username, exp);

            String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

            String signature = sign(encodedHeader + "." + encodedPayload);

            return encodedHeader + "." + encodedPayload + "." + signature;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean validateToken(String token) {
        if (token == null || token.isEmpty()) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;

        String signature = sign(parts[0] + "." + parts[1]);
        return parts[2].equals(signature);
    }

    private static String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }
}