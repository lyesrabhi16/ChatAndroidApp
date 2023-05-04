package com.example.chatapp;

import android.app.ProgressDialog;
import android.content.Context;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Friends {
    private int ID1,ID2;

    public Friends(int ID1, int ID2) {
        this.ID1 = ID1;
        this.ID2 = ID2;
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
    public void Save(Context ctx){
        ProgressDialog prgrs = new ProgressDialog(ctx);
        prgrs.setMessage("Adding Friend...");
        prgrs.show();
        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getAddFriendsURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                prgrs.dismiss();
                try {
                    JSONObject res = new JSONObject(response);
                    if(res.has("error"))
                        Toast.makeText(ctx, "error : "+res.get("error"), Toast.LENGTH_SHORT).show();
                    else{
                        Toast.makeText(ctx, "Response : "+res, Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e) {
                    Toast.makeText(ctx, "response handling error : "+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                prgrs.dismiss();
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
