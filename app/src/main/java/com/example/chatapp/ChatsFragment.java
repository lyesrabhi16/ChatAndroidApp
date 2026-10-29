package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.chatapp.databinding.FragmentChatsBinding;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/** The conversations (people and groups), most recent first, with unread counts and who is online. */
public class ChatsFragment extends Fragment {

    private FragmentChatsBinding BChats;
    private PersonItemAdapter adapter;
    private boolean loading, again;

    private final ChatEvents.Listener events = new ChatEvents.Listener() {
        @Override
        public void onChatMessage(JSONObject message) {
            refresh();
        }

        @Override
        public void onMessageUpdated(JSONObject message) {
            refresh();
        }

        @Override
        public void onGroupUpdated(int groupID, boolean removed) {
            refresh();
        }

        @Override
        public void onReconnected() {
            refresh();
        }

        @Override
        public void onPresence(int userID, boolean online, String lastSeen) {
            if (adapter == null) return;
            ArrayList<PersonItem> list = adapter.getList();
            for (int i = 0; i < list.size(); i++) {
                PersonItem item = list.get(i);
                if (!item.isGroup() && item.getUserID() == userID && item.isOnline() != online) {
                    item.setOnline(online);
                    adapter.notifyItemChanged(i);
                }
            }
        }
    };

    public ChatsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        BChats = FragmentChatsBinding.inflate(inflater, container, false);
        adapter = new PersonItemAdapter(new ArrayList<>(), position -> {
            PersonItem item = adapter.getList().get(position);
            Intent chat = new Intent(getContext(), ChatFragment.class);
            if (item.isGroup()) {
                chat.putExtra("groupID", item.getUserID());
                chat.putExtra("groupName", item.getTitle());
            } else {
                chat.putExtra("userID", item.getUserID());
                chat.putExtra("userName", item.getTitle());
                chat.putExtra("avatar", item.getAvatar());
            }
            startActivity(chat);
        });
        BChats.recyclerChats.setAdapter(adapter);
        BChats.emptyState.setVisibility(View.GONE);
        BChats.fabNewGroup.setOnClickListener(v -> startActivity(new Intent(getContext(), GroupCreateActivity.class)));
        Cache.conversations(requireContext(), cached -> {
            if (cached != null && BChats != null && adapter.getItemCount() == 0) show(cached);
        });
        return BChats.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        ChatEvents.add(events);
    }

    @Override
    public void onStop() {
        super.onStop();
        ChatEvents.remove(events);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();   // a conversation was probably read, or a message arrived, while another screen was open
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        BChats = null;
    }

    private void refresh() {
        if (BChats == null) return;
        if (loading) {   // a change arrived while loading: load once more when done
            again = true;
            return;
        }
        loading = true;
        if (adapter.getItemCount() == 0) BChats.progressBar.setVisibility(View.VISIBLE);
        Api.post(requireContext(), "/conversations", new JSONObject(), new Api.Callback() {
            @Override
            public void onSuccess(JSONObject json) {
                done();
                if (BChats == null) return;
                BChats.progressBar.setVisibility(View.GONE);
                JSONArray conversations = json.optJSONArray("conversations");
                Cache.saveConversations(requireContext(), conversations);
                show(conversations);
            }

            @Override
            public void onError(String message) {
                done();
                if (BChats == null) return;
                BChats.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void show(JSONArray conversations) {
        User user = User.getInstance(requireContext());
        ArrayList<PersonItem> list = new ArrayList<>();
        for (int i = 0; conversations != null && i < conversations.length(); i++) {
            JSONObject c = conversations.optJSONObject(i);
            if (c != null) list.add(toItem(c, user.getUserID()));
        }
        adapter.setList(list);
        BChats.emptyState.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void done() {
        loading = false;
        if (again) {
            again = false;
            refresh();
        }
    }

    private static PersonItem toItem(JSONObject c, int myID) {
        PersonItem item = new PersonItem();
        boolean group = "group".equals(c.optString("kind"));
        item.setKind(group ? PersonItem.GROUP : PersonItem.USER);
        item.setUserID(c.optInt("id"));
        item.setTitle(c.optString("name"));
        item.setAvatar(Api.str(c, "avatar"));
        item.setOnline(c.optBoolean("online"));
        item.setUnread(c.optInt("unread"));
        item.setData(Api.str(c, "date"));
        JSONObject preview = c.optJSONObject("preview");
        if (preview == null) {
            item.setSubTitle(group ? c.optInt("members") + " members" : "");
            return item;
        }
        String text = ChatItem.summary(preview.optString("type"), preview.optString("content"), null, preview.optBoolean("deleted"));
        String prefix = "";
        if (preview.optInt("sender") == myID) prefix = "You: ";
        else if (group && Api.str(preview, "senderName") != null) prefix = Api.str(preview, "senderName") + ": ";
        item.setSubTitle(prefix + text);
        return item;
    }
}
