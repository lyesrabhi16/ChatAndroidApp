package com.example.chatapp;

import android.content.Context;

import java.net.URISyntaxException;
import java.util.Collections;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketClient {
    private static Socket socket;

    public static Socket getSocket() {
        return socket;
    }

    public static void setSocket(Socket socket) {
        SocketClient.socket = socket;
    }

    public static Socket connectSocket(){
        try {
            // the server only accepts connections that present the token issued at sign in
            IO.Options options = new IO.Options();
            User user = User.peek();
            if (user != null && user.getToken() != null) {
                options.auth = Collections.singletonMap("token", user.getToken());
            }
            socket = IO.socket(Configs.getSocketURL(), options);
            socket.connect();

            socket.emit("message", "hello from client");

            return socket;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }
    public static void closeSocket(){
        if(socket == null) return;
        if(socket.connected())
        {
            socket.off();
            socket.disconnect();
        }
    }
}
