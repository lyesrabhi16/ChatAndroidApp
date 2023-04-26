package com.example.chatapp;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.FragmentChatBinding;
import com.example.chatapp.databinding.FragmentChatsBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;


public class ChatFragment extends AppCompatActivity {

    private FragmentChatBinding CBind;
    private int R_userID;
    private String R_userName;

    private ArrayList chatItemsList;

    private ChatItemAdapter chatItemAdapter;

    private Socket socket;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();

        CBind = FragmentChatBinding.inflate(getLayoutInflater());
        setContentView(CBind.getRoot());

        R_userID = getIntent().getIntExtra("userID", -1);
        R_userName = getIntent().getStringExtra("userName");

        CBind.buttonAccount.setText(R_userName);

        chatItemsList = new ArrayList<ChatItem>();

        chatItemAdapter = new ChatItemAdapter(chatItemsList);
        CBind.recylcerChat.setAdapter(chatItemAdapter);
        User user = User.getInstance(getApplicationContext());
        loadMessages(user.getUserID(), R_userID);

        socket = SocketClient.getSocket();
        socketListenersOff();
        socketListenersOn();


        CBind.buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        CBind.textinputLayoutSend.setEndIconOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = CBind.edittextMessage.getText().toString().trim();
                if( msg.isEmpty() ) return;
                User user = User.getInstance(getApplicationContext());
                JSONObject message = new JSONObject();
                try {
                    message.put("sender", user.getUserID());
                    message.put("receiver", R_userID);
                    message.put("content", msg);
                    message.put("date", getDate("yyyy-MM-dd HH:mm:ss") );

                    socket.emit("chat message", message.toString());
                    CBind.edittextMessage.setText("");

                } catch (JSONException e) {
                    Toast.makeText(CBind.getRoot().getContext(), "failed to send message", Toast.LENGTH_SHORT);
                }

            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        socketListenersOn();
    }

    @Override
    protected void onPause() {
        super.onPause();
        socketListenersOff();
    }

    public void socketListenersOn(){
        if(socket.hasListeners("chat message")) return;

        socket.on("chat message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JSONObject msg = new JSONObject(args[0].toString());
                            ChatItem item = new ChatItem();
                            item.setHeader(R_userName);
                            item.setMessage(msg.getString("content"));
                            item.setFooter(msg.getString("date"));
                            String[] d = item.getFooter().split(" ");
                            String date = d[0];
                            if(date.equals(getDate("dd-MM-yyyy"))){
                                String time = d[1];
                                item.setFooter(time);
                            }
                            else{
                                item.setFooter(date);
                            }

                            chatItemsList.add(item);
                            chatItemAdapter.setList(chatItemsList);
                            CBind.recylcerChat.setAdapter(chatItemAdapter);
                            CBind.recylcerChat.smoothScrollToPosition(chatItemsList.size()-1);


                        } catch (JSONException e) {
                            Toast.makeText(getApplicationContext(), "failed to receive message", Toast.LENGTH_SHORT);
                        }
                    }
                });
            }
        });
    }
    public void socketListenersOff(){
        socket.off("chat message");
    }

    public void loadMessages(int userID1, int userID2){

        CBind.progressBar2.setVisibility(View.VISIBLE);

        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getMessagesURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                CBind.progressBar2.setVisibility(View.GONE);
                try {
                    JSONObject res = new JSONObject(response);

                    if(res.has("error")){
                        Toast.makeText(getApplicationContext(), "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{
                        chatItemsList = new ArrayList<ChatItem>();
                        JSONArray resArr = res.getJSONArray("messages");
                        for (int i = 0; i < resArr.length(); i++) {
                            JSONObject r = resArr.getJSONObject(i);

                            ChatItem item = new ChatItem();
                            item.setHeader(R_userName);
                            item.setMessage(r.getString("content"));
                            item.setFooter(r.getString("date"));
                            String[] d = item.getFooter().split(" ");
                            String date = d[0];
                            if(date.equals(getDate("dd-MM-yyyy"))){
                                String time = d[1];
                                item.setFooter(time);
                            }
                            else{
                                item.setFooter(date);
                            }

                            chatItemsList.add(item);
                        }
                        chatItemAdapter.setList(chatItemsList);
                        CBind.recylcerChat.setAdapter(chatItemAdapter);
                        if (chatItemsList.size() > 0)
                          CBind.recylcerChat.smoothScrollToPosition(chatItemsList.size()-1);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                CBind.progressBar2.setVisibility(View.GONE);
                Toast.makeText(getApplicationContext(), "Connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userID1", ""+userID1);
                Params.put("userID2", ""+userID2);
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(getApplicationContext());
        reqQ.add(Sreq);
    }
    public String getDate(String format){
        String date;
        date = new SimpleDateFormat(format, Locale.getDefault()).format(new Date());
        return date;
    }
}