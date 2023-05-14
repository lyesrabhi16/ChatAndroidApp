package com.example.chatapp;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.FragmentChatBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class messages {
    int messageID, sender, receiver;
    String content, date;

    public messages() {
    }

    public messages(int messageID, int sender, int receiver, String content, String date) {
        this.messageID = messageID;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.date = date;
    }

    public int getMessageID() {
        return messageID;
    }

    public void setMessageID(int messageID) {
        this.messageID = messageID;
    }

    public int getSender() {
        return sender;
    }

    public void setSender(int sender) {
        this.sender = sender;
    }

    public int getReceiver() {
        return receiver;
    }

    public void setReceiver(int receiver) {
        this.receiver = receiver;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public static void loadMessages(int userID, int friendID, String  R_userName, Context ctx, onRequestFinish Req){


        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getMessagesURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject res = new JSONObject(response);

                    if(res.has("error")){
                        Toast.makeText(ctx, "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                        ArrayList l = new ArrayList();
                        l.add(res);
                        Req.onError(l);
                    }
                    else{
                        ArrayList chatItemsList = new ArrayList<ChatItem>();
                        JSONArray resArr = res.getJSONArray("messages");
                        for (int i = 0; i < resArr.length(); i++) {
                            JSONObject r = resArr.getJSONObject(i);

                            ChatItem item = new ChatItem();
                            if (userID == r.getInt("sender")){
                                item.setType(ChatItem.SENDER);
                                item.setHeader(User.getInstance(ctx).getUserName());
                            }
                            else{
                                item.setType(ChatItem.RECEIVER);
                                item.setHeader(R_userName);
                            }
                            item.setMessage(r.getString("content"));
                            /*item.setFooter(r.getString("date"));
                            String[] d = item.getFooter().split(" ");
                            String date = d[0];
                            if(date.equals(getCurrentDate("dd-MM-yyyy"))){
                                String time = d[1];
                                item.setFooter(time);
                            }
                            else{
                                item.setFooter(date);
                            }*/
                            item.setFooter("");

                            chatItemsList.add(item);
                        }
                        Req.onFinish(chatItemsList);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(ctx, "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                    ArrayList l = new ArrayList();
                    l.add(e);
                    Req.onError(l);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(ctx, "Connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                ArrayList l = new ArrayList();
                l.add(error);
                Req.onError(l);
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userID1", ""+userID);
                Params.put("userID2", ""+friendID);
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(ctx);
        reqQ.add(Sreq);
    }
    public static String getCurrentDate(String format){
        String date;
        date = new SimpleDateFormat(format, Locale.getDefault()).format(new Date());
        return date;
    }
}
