package com.example.chatapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.example.chatapp.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationBarView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {

    private Socket socket;
    private ActivityMainBinding MainBinding;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        MainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(MainBinding.getRoot());




        User user = User.getInstance(getApplicationContext());
        if(user.isLoggedin()){
            MainBinding.buttonAccount.setText(user.getUserName());
        }
        else{
            MainBinding.buttonAccount.setText("Account");
        }
        NavController navController = Navigation.findNavController(this, R.id.nav_host_main);
        MainBinding.navControllerMain.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                switch (item.getItemId()){
                    case R.id.chatsFragment:
                        navController.navigate(R.id.chatsFragment);
                        return true;
                    case R.id.peopleFragment:
                        navController.navigate(R.id.peopleFragment);
                        return true;
                }

                return false;
            }
        });
        socket = SocketClient.getSocket();
        if(socket == null)
            socket = SocketClient.connectSocket();
        
        socket.on("init request", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        socket.emit("message", "hello from client");
                        socket.emit("init", user.getUserID() + "");
                    }
                });
            }
        });

        socketListenersOff();
        socketListenersOn();





        MainBinding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query.isEmpty()){
                    //MainBinding.list.setAdapter(adapter);
                    return false;
                }
                search(query, MainBinding);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return onQueryTextSubmit(newText);
            }
        });

        MainBinding.buttonAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(MainActivity.this, AccountActivity.class);
                startActivity(i);
            }
        });



        /*
            ArrayList list = new ArrayList<PersonItem>();
            PersonItemAdapter adapter = new PersonItemAdapter(list);
            MainBinding.list.setAdapter(adapter);








            */

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketClient.closeSocket();
    }

    public void socketListenersOn(){
        if(socket.hasListeners("chat message")) return;
        socket.on("chat message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /*String msg = (String) args[0];
                        ChatItem item = new ChatItem();
                        item.setMessage(msg);
                        list.add(item);
                        adapter.notifyItemInserted(list.size()-1);
                        MainBinding.list.smoothScrollToPosition(list.size()-1);*/
                        Toast.makeText(getApplicationContext(), "message received in main: \n"+(String) args[0], Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    public void socketListenersOff(){
        socket.off("chat message");
    }
    private void search(String query, ActivityMainBinding mainBinding) {
        MainBinding.progressBar.setVisibility(View.VISIBLE);

        StringRequest Sreq = new StringRequest(Request.Method.POST, Configs.getSearchURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                MainBinding.progressBar.setVisibility(View.GONE);
                try {
                    JSONObject res = new JSONObject(response);

                    if(res.has("error")){
                        Toast.makeText(getApplicationContext(), "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{

                        JSONArray resArr = res.getJSONArray("results");
                        Toast.makeText(MainActivity.this, "Results: \n"+resArr.toString(), Toast.LENGTH_LONG).show();
                        /*ArrayList<PersonItem> l = new ArrayList<PersonItem>();
                        for (int i = 0; i < resArr.length(); i++) {
                            JSONObject r = resArr.getJSONObject(i);

                            PersonItem item = new PersonItem();
                            item.setUserID(r.getInt("userID"));
                            item.setUserEmail(r.getString("userEmail"));
                            item.setUserName(r.getString("userName"));

                            l.add(item);
                        }
                        PersonItemAdapter adapter = new PersonItemAdapter(l);
                        MainBinding.list.setAdapter(adapter);*/
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                MainBinding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getApplicationContext(), "Connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }){
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> Params = new HashMap<>();
                Params.put("searchTable", "users");
                Params.put("searchCols", "userName, userEmail");
                Params.put("searchTerm", query);
                return Params;
            }
        };

        RequestQueue reqQ = Volley.newRequestQueue(getApplicationContext());
        reqQ.add(Sreq);

    }



}