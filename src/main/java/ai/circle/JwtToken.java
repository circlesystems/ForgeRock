package ai.circle;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import org.forgerock.openam.auth.node.api.NodeProcessException;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtToken {
    private final static Logger logger = LoggerFactory.getLogger(JwtToken.class);
    private static final String ISSUER = "gocircle.ai";
    private static final String JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    /**
     * Generates a JWT
     * 
     * @param userName the username
     * @param expiry   the token expiry
     * @param secret   the secret for token signature
     * @throws Exception
     * @throws NodeProcessException
     * @throws JSONException
     */
    public String generateJwtToken(String userName, String expiry, String secret) throws Exception {
        try {
            String encodedHeader = encode(new JSONObject(JWT_HEADER));
            JSONObject payload = new JSONObject();
            payload.put("iat", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            payload.put("iss", ISSUER);
            payload.put(USERNAME, userName.replace("\"", ""));

            if (!expiry.isEmpty()) {
                try {
                    LocalDateTime expires = LocalDateTime.now().plusDays(Long.parseLong(expiry));
                    payload.put("exp", expires.toEpochSecond(ZoneOffset.UTC));
                } catch (Exception e) {
                    logger.error("Error on setting token expiracy. " + e.getMessage());
                    throw new Exception("Error on setting token expiracy");
                }
            }

            String signature = Crypto.hmac_sha256(secret, encodedHeader + "." + encode(payload));
            String circleJwt = (encodedHeader + "." + encode(payload) + "." + signature);

            return circleJwt;

        } catch (Exception e) {
            logger.error("Error generating JWT.. " + e.getMessage());
            throw new Exception("Error generating JWT. " + e.getMessage());
        }

    }

    private static String encode(JSONObject obj) {
        return encode(obj.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String decode(String encodedString) {
        return new String(Base64.getUrlDecoder().decode(encodedString));
    }

    /**
     * Decodes the payload and checks if there is an expiration date and validates
     * it.
     * 
     * @param String token
     * @return Boolean
     * @throws JSONException
     */
    private boolean isTokenNotExpired(String token) throws JSONException {
        String[] parts = token.split("\\.");
        JSONObject payload = new JSONObject();

        if (parts.length != 3) {
            logger.error("Invalid JWT.");
            return false;
        }

        try {
            payload = new JSONObject(decode(parts[1]));
        } catch (JSONException e) {
            logger.error("Error decoding payload. " + e.getMessage());
            return false;
        }

        if (payload.length() == 0) {
            logger.error("JWT payload is Empty.");
            return false;
        }

        // Check if the token is expired.
        if (payload.has("exp")) {
            try {
                return payload.getLong("exp") > (LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
            } catch (Exception e) {
                logger.error("Error checking the token expiry. " + e.getMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the JWT signature is valid.
     * 
     * @param token
     * @param secret
     * @return
     */
    private boolean isSignatureValid(String token, String secret) {

        try {
            String[] tokenParts = token.split("\\.");

            if (tokenParts.length != 3) {
                return false;
            }

            String signature = Crypto.hmac_sha256(secret, tokenParts[0].toString() + "." + tokenParts[1].toString());
            String signatureFromToken = tokenParts[2];
            return signature.equals(signatureFromToken);

        } catch (Exception e) {
            logger.error("Error validating signature. " + e.getMessage());
            return false;
        }
    }

    public Boolean isTokenValid(String token, String secret) {
        try {
            return isSignatureValid(token, secret) && isTokenNotExpired(token);
        } catch (JSONException e) {
            logger.error("Error validating the JWT." + e.getMessage());
            return false;
        }
    }

    public String userNameFromToken(String token) throws JSONException {
        String[] parts = token.split("\\.");
        JSONObject payload = new JSONObject();

        if (parts.length != 3) {
            return "";
        }

        try {
            payload = new JSONObject(decode(parts[1]));
            return payload.getString("username");
        } catch (JSONException e) {
            logger.error("Error decoding payload.");
            throw new JSONException(e);
        }

    }

}
