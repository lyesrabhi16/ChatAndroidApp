package com.example.chatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;

public class ChatItemAdapter extends RecyclerView.Adapter<ChatItemAdapter.ViewHolder> {

    private ArrayList<ChatItem> list;
    private static int SENDER = 0, RECEIVER = 1;
    private int userID;
    public ChatItemAdapter( ArrayList<ChatItem> list) {
        this.list = list;
    }


    public ArrayList<ChatItem> getList() {
        return list;
    }

    public void setList(ArrayList<ChatItem> list) {
        this.list = list;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        switch (list.get(position).getType()){
            case ChatItem.SENDER: return ChatItem.SENDER;
            case ChatItem.RECEIVER: return ChatItem.RECEIVER;
        }
        return 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType){
            // your own messages on the right, the other person's on the left
            case ChatItem.SENDER:
                return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_item_right, parent, false));
            case ChatItem.RECEIVER:
                return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_item, parent, false));
        }

        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.chat_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatItem item = list.get(position);
        holder.Header.setText(item.getHeader());
        holder.Message.setText(item.getMessage());
        holder.Footer.setText(item.getFooter());

    }

    @Override
    public int getItemCount() {
        return list.size();
    }
    public static class ViewHolder extends RecyclerView.ViewHolder{
        private TextView Message, Header, Footer;
        public ViewHolder(View view){
            super(view);
            Message = view.findViewById(R.id.body);
            Header = view.findViewById(R.id.header);
            Footer = view.findViewById(R.id.footer);
        }
    }
}
