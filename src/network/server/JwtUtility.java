package network.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class JwtUtility {

    private static final String SECRET = initializeSecret();

    private static String initializeSecret() {
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.trim().isEmpty()) {
            return envSecret;
        }
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String generateToken(String username, String clientId) {
        try {
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            long exp = (System.currentTimeMillis() / 1000) + (60 * 60 * 24);
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

    // 🔴 FIX M-26: درخواست expectedClientId برای جلوگیری از سرقت توکن
    public static boolean validateToken(String token, String expectedClientId) {
        if (token == null || token.isEmpty()) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;

        String signature = sign(parts[0] + "." + parts[1]);
        if (!parts[2].equals(signature)) return false;

        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject payloadObj = JsonParser.parseString(payloadJson).getAsJsonObject();

            if (payloadObj.has("exp")) {
                long expTime = payloadObj.get("exp").getAsLong();
                if ((System.currentTimeMillis() / 1000) > expTime) {
                    System.out.println("🚨 [Security] JWT Token has expired!");
                    return false;
                }
            } else {
                return false;
            }

            // 🔴 FIX M-26: بایند کردن اکانت به اتصال فیزیکی سوکت
            if (payloadObj.has("sub")) {
                String sub = payloadObj.get("sub").getAsString();
                if (!sub.equals(expectedClientId)) {
                    System.out.println("🚨 [Security] JWT Token hijacked or bound to different client ID!");
                    return false;
                }
            } else {
                return false;
            }

        } catch (Exception e) {
            return false;
        }

        return true;
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