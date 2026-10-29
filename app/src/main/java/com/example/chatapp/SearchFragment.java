package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;

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
import com.example.chatapp.databinding.ActivityMainBinding;
import com.example.chatapp.databinding.FragmentSearchBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class SearchFragment extends Fragment {

    private FragmentSearchBinding SBind;
    private PersonItemAdapter adapter;
    private ArrayList personItemArrayList;



    private static String Query;
    public SearchFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SBind = FragmentSearchBinding.inflate(getLayoutInflater());

        personItemArrayList = new ArrayList<PersonItem>();
        adapter = new PersonItemAdapter(personItemArrayList, new RecyclerViewInterface() {
            @Override
            public void onItemClick(int position) {
                Intent account = new Intent(getContext(), AccountActivity.class);
                account.putExtra("userID", adapter.getList().get(position).getUserID());
                account.putExtra("userName", adapter.getList().get(position).getTitle());
                startActivity(account);
            }
        });
        SBind.recyclerSearch.setAdapter(adapter);
        if(Query != null)
            search(Query);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        SBind = FragmentSearchBinding.inflate(inflater, container, false);
        return SBind.getRoot();
    }
    public static String getQuery() {
        return Query;
    }

    public static void setQuery(String query) {
        Query = query;
    }
    private void search(String query) {
        SBind.progressBar.setVisibility(View.VISIBLE);

        StringRequest Sreq = new AuthRequest(Request.Method.POST, Configs.getSearchURL(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                SBind.progressBar.setVisibility(View.GONE);
                try {
                    JSONObject res = new JSONObject(response);

                    if(res.has("error")){
                        Toast.makeText(getContext(), "error: "+ res.get("error"), Toast.LENGTH_LONG).show();
                    }
                    else{

                        JSONArray resArr = res.getJSONArray("results");
                        if( resArr.length() < 1){
                            Toast.makeText(getContext(), "0 results found.", Toast.LENGTH_SHORT).show();
                        }
                        else{
                            ArrayList<PersonItem> l = new ArrayList<PersonItem>();
                            for (int i = 0; i < resArr.length(); i++) {
                                JSONObject r = resArr.getJSONObject(i);
                                if(r.getInt("userID") == User.getInstance(getContext()).getUserID()){
                                    continue;
                                }
                                else{
                                    PersonItem item = new PersonItem();
                                    item.setUserID(r.getInt("userID"));
                                    item.setSubTitle(r.getString("userEmail"));
                                    item.setTitle(r.getString("userName"));

                                    l.add(item);
                                }
                            }
                            adapter.setList(l);
                            SBind.recyclerSearch.setAdapter(adapter);
                        }

                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Response error: "+ e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                SBind.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Connection error: " + error.getMessage(), Toast.LENGTH_LONG).show();
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

        RequestQueue reqQ = Volley.newRequestQueue(getContext());
        reqQ.add(Sreq);

    }
}