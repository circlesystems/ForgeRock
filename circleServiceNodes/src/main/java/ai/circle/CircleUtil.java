/**
 * Copyright 2021 Circle
 */

package ai.circle;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONObject;

public class CircleUtil {
    public final static String OUT_PARAMETER = "circleJsResult";
    public final static String TRUE_OUTCOME_ID = "isRunningTrue";
    public final static String FALSE_OUTCOME_ID = "isRunningFalse";
    public final static String CORE_SCRIPT = readFileString("/js/authorize.js");
    private final static Logger logger = LoggerFactory.getLogger(CircleUtil.class);

    /**
     * Read file content into a string
     * 
     * @param fileName
     * @throws IOException file does not exist or cannot read the content
     */

    public static String readFileAsString(String fileName) throws IOException {
        String text = "";
        try {
            text = new String(Files.readAllBytes(Paths.get(fileName)));
        } catch (IOException e) {
            throw new IOException("Cant read file " + fileName);
        }
        return text;
    }

    public static String readFileString(String path) {
        try {
            InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            String data = readAllLines(in);
            in.close();
            return data;
        } catch (Exception e) {

            logger.error("Can´t read file " + path, e);
            return null;
        } finally {

        }
    }

    public static String readAllLines(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return reader.lines().parallel().collect(Collectors.joining("\n"));
    }

    public static String createClientSideScriptExecutorFunction(String script, String outputParameterId,
            boolean clientSideScriptEnabled, String context) {
        String collectingDataMessage = "";

        if (clientSideScriptEnabled) {
            collectingDataMessage = "  messenger.messages.addMessage( message );\n";
        }

        String browserScript = "if (window.require) {\n"
                + "    var messenger = require(\"org/forgerock/commons/ui/common/components/Messages\"),\n" //
                + "        spinner = require(\"org/forgerock/commons/ui/common/main/SpinnerManager\"),\n" //
                + "        message = {message:\"Collecting Data...\", type:\"info\"};\n" //
                + "    spinner.showSpinner();\n"//
                + collectingDataMessage + "}";

        return String.format(browserScript + "(async function (output) {\n"//
                + "    var autoSubmitDelay = 0,\n"//
                + "        submitted = false,\n"//
                + "        context = %s;\n"//
                + "    function submit() {\n"//
                + "        if (submitted) {\n"//
                + "            return;\n" //
                + "        }"//
                + "        if (!(typeof $ == 'function')) {\n"//
                + "            document.getElementById('loginButton_0').click();\n"//
                + "        }else {\n" //
                + "          $('input[type=submit]').click();\n"//
                + "        }\n" //
                + "        submitted = true;\n"//
                + "    }\n"//
                + "%s \n" // script
                + " async function autoSubmit() {\n"//
                + "    setTimeout(submit, autoSubmitDelay);\n" //
                + "    }\n" + "}) (document.forms[0].elements['%s']);\n", //
                context, script, outputParameterId);
    }

    public static String authorizeForAuthenticationCode(String authId, String endPoint, String redirectURL,
            String clientID) {

        try {
            URL url = new URL(endPoint);

            // Build HTTP request
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            HttpURLConnection.setFollowRedirects(true);
            conn.setInstanceFollowRedirects(true);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            boolean redirect = false;

            // Set the headers
            conn.setRequestProperty("Accept-API-Version", "resource=2.0, protocol=1.0");
            conn.setRequestProperty("Cookie",
                    "iPlanetDirectoryPro=" + authId + "; Path=/; Domain=.partner.com; HttpOnly;");

            String urlParameters = "redirect_uri=" //
                    + redirectURL //
                    + "&scope=write&response_type=code&client_id=" //
                    + clientID + "&csrf=" //
                    + authId + "&state=abc123&decision=allow";

            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;

            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            conn.setUseCaches(false);

            try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
                wr.write(postData);
            }

            int status = conn.getResponseCode();

            if (status != HttpURLConnection.HTTP_OK) {
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
                        || status == HttpURLConnection.HTTP_SEE_OTHER)

                    redirect = true;
            }

            if (redirect) {
                String redirectUrl = conn.getHeaderField("Location");
                String code = redirectUrl.split("code=")[1].split("&")[0];

                return code;
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map<String, String> getQueryMap(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<String, String>();

        for (String param : params) {
            String name = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(name, value);
        }
        return map;
    }

    public static String authenticateWithUsernamePassword(String userName, String userPassword, String endPoint) {
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost post = new HttpPost(endPoint);

        post.setHeader("X-OpenAM-Username", userName.replace("\"", ""));
        post.setHeader("X-OpenAM-Password", userPassword.replace("\"", ""));
        post.setHeader("Accept-API-Version", "resource=2.0, protocol=1.0");

        try {
            HttpResponse response = client.execute(post);
            String json = EntityUtils.toString(response.getEntity());
            JSONObject jret = new JSONObject(json);
            String token = jret.getString("tokenId");
            return token;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    public static HashMap<String, String> getForgeRockRefreshTokenFromAuthCode(String code, String redirectUrl,
            String clientId, String clientSecret, String endPoint) {
        try {

            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(endPoint);

            post.setHeader("Accept-API-Version", "resource=2.0, protocol=1.0");

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("client_id", clientId));
            params.add(new BasicNameValuePair("client_secret", clientSecret));
            params.add(new BasicNameValuePair("code", code));
            params.add(new BasicNameValuePair("grant_type", "authorization_code"));
            params.add(new BasicNameValuePair("redirect_uri", redirectUrl));

            post.setEntity(new UrlEncodedFormEntity(params));

            HttpResponse response = client.execute(post);

            String json = EntityUtils.toString(response.getEntity());

            JSONObject jret = new JSONObject(json);
            String refreshToken = jret.getString("refresh_token");
            String accessToken = jret.getString("access_token");

            HashMap<String, String> ret = new HashMap<String, String>();
            ret.put("refreshToken", refreshToken);
            ret.put("accessToken", accessToken);
            return ret;

        } catch (Exception e) {

            e.printStackTrace();
        }
        return null;
    }

    public static HashMap<String, String> getForgeRockAccessTokenFromRefreshToken(String clientId, String clientSecret,
            String refreshToken, String endPoint) {

        try {
            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(endPoint);

            post.setHeader("Accept-API-Version", "resource=2.0, protocol=1.0");

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("client_id", clientId));
            params.add(new BasicNameValuePair("client_secret", clientSecret));
            params.add(new BasicNameValuePair("refresh_token", refreshToken));
            params.add(new BasicNameValuePair("grant_type", "refresh_token"));
            post.setEntity(new UrlEncodedFormEntity(params));

            HttpResponse response = client.execute(post);

            String json = EntityUtils.toString(response.getEntity());

            JSONObject jret = new JSONObject(json);
            String newRefreshToken = jret.getString("refresh_token");
            String newAccessToken = jret.getString("access_token");

            HashMap<String, String> ret = new HashMap<String, String>();
            ret.put("refreshToken", newRefreshToken);
            ret.put("accessToken", newAccessToken);
            return ret;

        } catch (Exception e) {

            e.printStackTrace();
        }
        return null;
    }

}
