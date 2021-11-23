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
import java.util.stream.Collectors;

import javax.security.auth.callback.Callback;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.forgerock.openam.auth.node.api.NodeProcessException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.authentication.callbacks.ScriptTextOutputCallback;

public class CircleUtil {
    private final static Logger logger = LoggerFactory.getLogger(CircleUtil.class);

    public final static String OUT_PARAMETER = "circleJsResult";
    public final static String CORE_SCRIPT = readFileString("/js/authorize.js");

    /**
     * Read file content into a string
     *
     * @param fileName
     * @throws IOException file does not exist or cannot read the content
     */

    public static String readFileString(String path) {
        try {
            InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            String data = readAllLines(in);
            in.close();
            return data;
        } catch (NullPointerException | IOException e) {
            logger.error("Can´t read file " + path, e);
            return null;
        }
    }

    public static String readAllLines(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return reader.lines().parallel().collect(Collectors.joining("\n"));
    }

    public static ImmutableList<Callback> getScriptAndSelfSubmitCallback(String circleNodeScript) {
        String clientSideScriptExecutorFunction = CircleUtil.createClientSideScriptExecutorFunction(circleNodeScript,
                CircleUtil.OUT_PARAMETER, true);

        ScriptTextOutputCallback scriptAndSelfSubmitCallback = new ScriptTextOutputCallback(
                clientSideScriptExecutorFunction);

        HiddenValueCallback hiddenValueCallback = new HiddenValueCallback(CircleUtil.OUT_PARAMETER);
        return ImmutableList.of(scriptAndSelfSubmitCallback, hiddenValueCallback);

    }

    public static String createClientSideScriptExecutorFunction(String script, String outputParameterId,
            boolean clientSideScriptEnabled) {
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
                + "        context = {};\n"//
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
                + "    }\n"// ''
                + "%s \n" // script
                + " async function autoSubmit() {\n"//
                + "    setTimeout(submit, autoSubmitDelay);\n" //
                + "    }\n" + "}) (document.forms[0].elements['%s']);\n", //
                script, outputParameterId);
    }


}
