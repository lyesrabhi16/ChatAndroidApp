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
        if (getSupportActionBar() != null) getSupportActionBar().hide();  // the theme has no action bar
        MainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(MainBinding.getRoot());




        User user = User.getInstance(getApplicationContext());
        if(user.isLoggedin()){
            Avatars.apply(MainBinding.buttonAccount, user.getUserName());
            Friends.loadFriends(getApplicationContext());
        }
        else{
            Avatars.apply(MainBinding.buttonAccount, "?");
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
                    case R.id.searchFragment:
                        navController.navigate(R.id.searchFragment);
                        return true;
                }

                return false;
            }
        });
        socket = SocketClient.connectSocket();  // reuses the live connection after a rotation or theme change
        
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
                else{
                    SearchFragment.setQuery(query);
                    MainBinding.navControllerMain.setSelectedItemId(R.id.searchFragment);
                }
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
                Intent account = new Intent(MainActivity.this, AccountActivity.class);
                account.putExtra("userID", user.getUserID());
                startActivity(account);
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
        // a rotation or dark-mode switch recreates this activity: only a real exit closes the connection
        if (isFinishing()) SocketClient.closeSocket();
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




}