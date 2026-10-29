package com.example.chatapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import org.junit.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Checks, with the same Socket.IO client library the app uses, that the server's authentication works end to end:
 * a token from /signup opens a connection, a missing or bad token is refused.
 *
 * It needs the server (docker compose up) and is skipped otherwise:
 *   CHAT_SERVER_HOST=localhost ./gradlew testDebugUnitTest
 */
public class SocketAuthIntegrationTest {

    private static String signUpAndGetToken(String host) throws Exception {
        String body = "userName=itest&userEmail=itest" + System.nanoTime() + "@example.com&userPassword=secret123";
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + host + ":8001/signup").openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");  // what Volley sends
        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
        String response = new Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A").next();
        Matcher m = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(response);
        assertTrue("sign up should return a token: " + response, m.find());
        return m.group(1);
    }

    /** Opens a connection; returns "connected" or the server's refusal message. */
    private static String tryToConnect(String host, String token) throws Exception {
        IO.Options options = new IO.Options();
        options.transports = new String[]{"websocket"};
        options.reconnection = false;
        if (token != null) options.auth = Collections.singletonMap("token", token);
        Socket socket = IO.socket(URI.create("http://" + host + ":8000"), options);

        CountDownLatch done = new CountDownLatch(1);
        final String[] outcome = {null};
        socket.on(Socket.EVENT_CONNECT, args -> { outcome[0] = "connected"; done.countDown(); });
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> { outcome[0] = String.valueOf(args[0]); done.countDown(); });
        socket.connect();
        assertTrue("no answer from the server", done.await(5, TimeUnit.SECONDS));
        socket.close();
        return outcome[0];
    }

    @Test
    public void tokenOpensTheConnectionAndAMissingOrBadTokenIsRefused() throws Exception {
        String host = System.getenv("CHAT_SERVER_HOST");
        assumeNotNull(host);  // no server: skip

        String token = signUpAndGetToken(host);
        assertNotNull(token);

        assertEquals("connected", tryToConnect(host, token));

        String withoutToken = tryToConnect(host, null);
        assertFalse(withoutToken.equals("connected"));
        assertTrue(withoutToken, withoutToken.contains("authentication required"));

        String badToken = tryToConnect(host, token + "x");
        assertTrue(badToken, badToken.contains("authentication required"));
    }
}
