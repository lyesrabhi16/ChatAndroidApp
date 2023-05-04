package com.example.chatapp;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Toast;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.chatapp.databinding.ActivityAccountBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class AccountActivity extends AppCompatActivity {

    private ActivityAccountBinding BAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BAccount = ActivityAccountBinding.inflate(getLayoutInflater());
        setContentView(BAccount.getRoot());
        User user = User.getInstance(getApplicationContext());
        int accountID = getIntent().getIntExtra("userID", -1);
        if ( accountID == user.getUserID()){
            BAccount.buttonChangePassword.setVisibility(View.VISIBLE);
            BAccount.buttonLogout.setVisibility(View.VISIBLE);
            BAccount.buttonAddFriend.setVisibility(View.GONE);

            BAccount.username.setText(user.getUserName());
            BAccount.email.setText(user.getUserEmail());


            BAccount.buttonChangePassword.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Toast.makeText(AccountActivity.this, "change password to be implemented", Toast.LENGTH_SHORT).show();
                }
            });
            BAccount.buttonLogout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    user.logout();
                    Intent signin = new Intent(AccountActivity.this, SignInActivity.class);
                    signin.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    signin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(signin);
                    finish();
                }
            });

        }
        else{
            BAccount.buttonChangePassword.setVisibility(View.GONE);
            BAccount.buttonLogout.setVisibility(View.GONE);
            BAccount.buttonAddFriend.setVisibility(View.VISIBLE);

            getUser(accountID);

            BAccount.buttonAddFriend.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Toast.makeText(AccountActivity.this, "add friend to be implemented", Toast.LENGTH_SHORT).show();
                }
            });
        }


        BAccount.buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

    }
    private void getUser(int id){
        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getUserURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject res = new JSONObject(response);
                    if(res.has("error")){
                        Toast.makeText(AccountActivity.this, "Error: "+res.getString("error"), Toast.LENGTH_SHORT).show();
                    }
                    else{
                        BAccount.username.setText(res.getString("userName"));
                        BAccount.email.setText(res.getString("userEmail"));
                    }
                } catch (JSONException e) {
                    Toast.makeText(AccountActivity.this, "Response Handling Error : "+e.getMessage(), Toast.LENGTH_SHORT).show();
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(AccountActivity.this, "Connection Error.", Toast.LENGTH_SHORT).show();
            }
        }){
            @Nullable
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String,String> Params = new HashMap<>();
                Params.put("userID", id+"");
                return Params;
            }
        };
        RequestQueue reqQ = Volley.newRequestQueue(getApplicationContext());
        reqQ.add(Sreq);
    }
}