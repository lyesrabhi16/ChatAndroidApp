package com.example.chatapp;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/** People, conversations and group members: avatar, name, a line of text, and optionally the time, an unread badge, a presence dot or a checkbox. */
public class PersonItemAdapter extends RecyclerView.Adapter<PersonItemAdapter.ViewHolder> {

    private ArrayList<PersonItem> list;
    private final RecyclerViewInterface recyclerViewInterface;
    private boolean selectable;

    public PersonItemAdapter(ArrayList<PersonItem> list, RecyclerViewInterface recyclerViewInterface) {

        this.list = list;
        this.recyclerViewInterface = recyclerViewInterface;
    }

    public ArrayList<PersonItem> getList() {
        return list;
    }

    public void setList(ArrayList<PersonItem> list) {
        this.list = list;
        notifyDataSetChanged();
    }

    /** Shows a checkbox on every row (choosing the members of a group). */
    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PersonItemAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PersonItemAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.person_item, parent, false), recyclerViewInterface);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonItemAdapter.ViewHolder holder, int position) {
        PersonItem item = list.get(position);
        holder.Title.setText(item.getTitle());
        holder.subTitle.setText(item.getSubTitle());
        Avatars.apply(holder.avatar, item.getTitle(), item.getAvatar());
        String time = TimeFormat.list(item.getData());  // only the Chats tab has a date
        holder.time.setText(time);
        holder.time.setVisibility(time.isEmpty() ? View.GONE : View.VISIBLE);

        boolean unread = item.getUnread() > 0;
        holder.badge.setVisibility(unread ? View.VISIBLE : View.GONE);
        holder.badge.setText(item.getUnread() > 99 ? "99+" : String.valueOf(item.getUnread()));
        holder.Title.setTypeface(null, unread ? Typeface.BOLD : Typeface.BOLD);
        holder.subTitle.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        holder.online.setVisibility(item.isOnline() ? View.VISIBLE : View.GONE);
        holder.check.setVisibility(selectable ? View.VISIBLE : View.GONE);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(item.isSelected());
        holder.check.setOnCheckedChangeListener((box, checked) -> item.setSelected(checked));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar, time, badge;
        private final TextView Title, subTitle;
        private final View online;
        private final CheckBox check;
        public ViewHolder(View view, RecyclerViewInterface recyclerViewInterface){
            super(view);
            avatar = view.findViewById(R.id.Avatar);
            time = view.findViewById(R.id.Time);
            badge = view.findViewById(R.id.Badge);
            online = view.findViewById(R.id.OnlineDot);
            check = view.findViewById(R.id.Check);
            Title = view.findViewById(R.id.Title);
            subTitle = view.findViewById(R.id.subTitle);

            view.setOnClickListener(v -> {
                if (selectable) check.toggle();
                recyclerViewInterface.onItemClick(getAdapterPosition());
            });
        }

    }
}
