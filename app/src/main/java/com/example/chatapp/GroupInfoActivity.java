package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.chatapp.databinding.ActivityGroupInfoBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** A group: its name, its members (admins can add, remove and rename) and a way to leave. */
public class GroupInfoActivity extends AppCompatActivity {

    private ActivityGroupInfoBinding b;
    private PersonItemAdapter adapter;
    private int groupID, myID;
    private boolean admin;
    private String groupName = "";

    private final ChatEvents.Listener events = new ChatEvents.Listener() {
        @Override
        public void onGroupUpdated(int id, boolean removed) {
            if (id != groupID) return;
            if (removed) finish();
            else load();
        }

        @Override
        public void onPresence(int userID, boolean online, String lastSeen) {
            load();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        b = ActivityGroupInfoBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        groupID = getIntent().getIntExtra("groupID", -1);
        myID = User.getInstance(getApplicationContext()).getUserID();

        adapter = new PersonItemAdapter(new ArrayList<>(), position -> onMember(adapter.getList().get(position)));
        b.recyclerMembers.setAdapter(adapter);
        b.buttonBack.setOnClickListener(v -> finish());
        b.buttonAddMembers.setOnClickListener(v -> {
            Intent add = new Intent(this, GroupCreateActivity.class);
            add.putExtra("groupID", groupID);
            startActivity(add);
        });
        b.buttonRename.setOnClickListener(v -> rename());
        b.buttonLeave.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Leave \"" + groupName + "\"?")
                .setMessage("You will stop receiving its messages.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Leave", (d, w) -> remove(myID, true))
                .show());
    }

    @Override
    protected void onStart() {
        super.onStart();
        ChatEvents.add(events);
        load();   // members may have been added from the picker
    }

    @Override
    protected void onStop() {
        super.onStop();
        ChatEvents.remove(events);
    }

    private void load() {
        Api.post(this, "/groups/info", Api.body("groupID", groupID), new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                JSONObject group = json.optJSONObject("group");
                if (group == null) return;
                groupName = group.optString("name");
                admin = "admin".equals(group.optString("me"));
                JSONArray members = group.optJSONArray("members");
                int count = members == null ? 0 : members.length();
                b.groupTitle.setText(groupName);
                Avatars.apply(b.groupAvatar, groupName);
                b.groupSubtitle.setText(count + " members");
                b.adminActions.setVisibility(admin ? View.VISIBLE : View.GONE);   // add members and rename: admins only

                ArrayList<PersonItem> list = new ArrayList<>();
                for (int i = 0; members != null && i < members.length(); i++) {
                    JSONObject m = members.optJSONObject(i);
                    if (m == null) continue;
                    PersonItem item = new PersonItem();
                    item.setUserID(m.optInt("userID"));
                    item.setTitle(m.optInt("userID") == myID ? m.optString("userName") + " (you)" : m.optString("userName"));
                    item.setAvatar(Api.str(m, "avatar"));
                    item.setOnline(m.optBoolean("online"));
                    item.setSubTitle("admin".equals(m.optString("role")) ? "Admin" : "Member");
                    list.add(item);
                }
                adapter.setList(list);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void onMember(PersonItem item) {
        if (item.getUserID() == myID) return;
        if (!admin) {
            openProfile(item.getUserID());
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.getTitle())
                .setItems(new String[]{"View profile", "Remove from group"}, (d, which) -> {
                    if (which == 0) openProfile(item.getUserID());
                    else remove(item.getUserID(), false);
                })
                .show();
    }

    private void openProfile(int userID) {
        Intent account = new Intent(this, AccountActivity.class);
        account.putExtra("userID", userID);
        startActivity(account);
    }

    private void rename() {
        EditText input = new EditText(this);
        input.setText(groupName);
        input.setSelection(groupName.length());
        input.setSingleLine();
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(input);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename group")
                .setView(holder)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) ->
                        Api.post(this, "/groups/rename", Api.body("groupID", groupID, "name", input.getText().toString()), new Api.Callback() {
                            @Override
                            public void onSuccess(JSONObject json) {
                                load();
                            }

                            @Override
                            public void onError(String message) {
                                toast(message);
                            }
                        }))
                .show();
    }

    private void remove(int userID, boolean leaving) {
        Api.post(this, "/groups/remove", Api.body("groupID", groupID, "userID", userID), new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                if (leaving) {
                    // the conversation screen below this one closes itself when it is told we are gone
                    finish();
                } else {
                    load();
                }
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
