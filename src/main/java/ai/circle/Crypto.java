/**
 * Copyright 2021 Circle
 */
package ai.circle;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.api.client.util.Base64;

public class Crypto {

    public static String hmac_sha256(String secretKey, String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            return (Base64.encodeBase64String(sha256_HMAC.doFinal(data.getBytes())));
        } catch (Exception e) {
            //TODO Handle exception with logger and NodeProcessException
            return e.getMessage();
        }

    }

}
