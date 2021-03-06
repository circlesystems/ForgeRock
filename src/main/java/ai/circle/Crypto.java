/**
 * Copyright 2021 Circle
 */
package ai.circle;

import java.nio.charset.StandardCharsets;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.api.client.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Crypto {
    private final static Logger logger = LoggerFactory.getLogger(Crypto.class);

    public static String hmac_sha256(String secretKey, String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            return (Base64.encodeBase64String(sha256_HMAC.doFinal(data.getBytes())));
        } catch (Exception e) {
            logger.error("Error generating hmac_sha256", e);
            return null;
        }

    }

}
