package com.example.chatapp;

import java.net.URISyntaxException;
import java.util.Collections;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * The one Socket.IO connection of the app, shared by the main screen and the conversation screen.
 *
 * It outlives a single activity: a rotation or a dark-mode switch recreates the activities but must not cut the
 * connection. It is closed for good only when the user leaves the app or signs out (see closeSocket()).
 */
public class SocketClient {
    private static Socket socket;
    // true once closeSocket() has been called: a disconnected socket never reconnects by itself after that
    private static boolean closed = true;

    /** The live connection, opened on demand. */
    public static synchronized Socket getSocket() {
        if (socket == null || closed) {
            return connectSocket();
        }
        return socket;
    }

    public static void setSocket(Socket socket) {
        SocketClient.socket = socket;
    }

    /** Returns the current connection, or opens a new one when there is none (or it was closed). */
    public static synchronized Socket connectSocket(){
        if (socket != null && !closed) {
            return socket;
        }
        try {
            // the server only accepts connections that present the token issued at sign in
            IO.Options options = new IO.Options();
            User user = User.peek();
            if (user != null && user.getToken() != null) {
                options.auth = Collections.singletonMap("token", user.getToken());
            }
            socket = IO.socket(Configs.getSocketURL(), options);
            socket.connect();
            closed = false;

            socket.emit("message", "hello from client");

            return socket;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static synchronized void closeSocket(){
        closed = true;
        if(socket == null) return;
        socket.off();
        socket.disconnect();
    }
}
