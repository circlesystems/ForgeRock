/**
 * Copyright 2021 Circle
 */
package ai.circle;

import javax.crypto.Mac;
import org.apache.commons.codec.binary.Base64;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    public static String hmac_sha256(String secretKey, String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            String hash = Base64.encodeBase64String(sha256_HMAC.doFinal(data.getBytes()));
            return (hash);
        } catch (Exception e) {
            return e.getMessage();
        }

    }
}