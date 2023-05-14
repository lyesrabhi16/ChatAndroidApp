package com.example.chatapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.ActivityAccountBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Friends {
    private int ID1,ID2;
    private static Set<Integer> friends = new HashSet<>();
    public Friends(int ID1, int ID2) {
        this.ID1 = ID1;
        this.ID2 = ID2;
    }

    public static Set<Integer> getFriends() {
        return friends;
    }

    public static void setFriends(Set<Integer> friends0) {
        friends = friends0;
    }

    public int getID1() {
        return ID1;
    }

    public void setID1(int ID1) {
        this.ID1 = ID1;
    }

    public int getID2() {
        return ID2;
    }

    public void setID2(int ID2) {
        this.ID2 = ID2;
    }
    public static void loadFriends(Context ctx){
        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getFriendsURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject res = new JSONObject(response);
                    if(res.has("error")){
                        Toast.makeText(ctx, "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{
                        JSONArray resArr = res.getJSONArray("friends");
                        if(resArr.length()<1) {
                            Toast.makeText(ctx, "0 friends", Toast.LENGTH_LONG).show();
                            return;
                        }
                        for (int i = 0; i < resArr.length(); i++) {

                            JSONObject r = resArr.getJSONObject(i);
                            friends.add(r.getInt("userID"));

                        }
                    }


                } catch (JSONException e) {
                    //BPeople.progressBarPeople.setVisibility(View.GONE);
                    e.printStackTrace();
                    Toast.makeText(ctx, "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(ctx, "Connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userID", User.getInstance(ctx).getUserID()+"");
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(ctx);
        reqQ.add(Sreq);
    }
    public void Add(Context ctx, ActivityAccountBinding BAccount){

        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getAddFriendsURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject res = new JSONObject(response);
                    if(res.has("error"))
                        Toast.makeText(ctx, "error : "+res.get("error"), Toast.LENGTH_SHORT).show();
                    else{
                        int userID = User.getInstance(ctx).getUserID();
                        if ( userID == ID1)
                            friends.add(ID2);
                        else{
                            friends.add(ID1);
                        }
                        Toast.makeText(ctx, "Friend Added", Toast.LENGTH_SHORT).show();
                        BAccount.buttonAddFriend.setVisibility(View.GONE);
                        BAccount.buttonDeleteFriend.setVisibility(View.VISIBLE);
                    }
                } catch (JSONException e) {
                    Toast.makeText(ctx, "response handling error : "+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(ctx, "connection error: "+error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userID1", ID1+"");
                Params.put("userID2", ID2+"");
                return Params;
            }
        };
        RequestQueue Qreq = Volley.newRequestQueue(ctx);
        Qreq.add(Sreq);
    }
    public void Delete(Context ctx, ActivityAccountBinding BAccount){

        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getRemoveFriendsURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject res = new JSONObject(response);
                    if(res.has("error"))
                        Toast.makeText(ctx, "error : "+res.get("error"), Toast.LENGTH_SHORT).show();
                    else{
                        int userID = User.getInstance(ctx).getUserID();
                        if ( userID == ID1)
                            friends.remove(ID2);
                        else{
                            friends.remove(ID1);
                        }
                        Toast.makeText(ctx, "Friend Removed", Toast.LENGTH_SHORT).show();
                        BAccount.buttonAddFriend.setVisibility(View.VISIBLE);
                        BAccount.buttonDeleteFriend.setVisibility(View.GONE);
                    }
                } catch (JSONException e) {
                    Toast.makeText(ctx, "response handling error : "+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(ctx, "connection error: "+error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("userID1", ID1+"");
                Params.put("userID2", ID2+"");
                return Params;
            }
        };
        RequestQueue Qreq = Volley.newRequestQueue(ctx);
        Qreq.add(Sreq);
    }
}
