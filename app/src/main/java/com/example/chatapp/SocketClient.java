package com.example.chatapp;

import android.content.Context;

import java.net.URISyntaxException;

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
            socket = IO.socket(Configs.getSocketURL());
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
