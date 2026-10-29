package com.example.chatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class PersonItemAdapter extends RecyclerView.Adapter<PersonItemAdapter.ViewHolder> {

    private ArrayList<PersonItem> list;
    private final RecyclerViewInterface recyclerViewInterface;
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
        Avatars.apply(holder.avatar, item.getTitle());
        String time = TimeFormat.list(item.getData());  // only the Chats tab has a date
        holder.time.setText(time);
        holder.time.setVisibility(time.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }



    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView avatar, time;
        private TextView Title, subTitle;
        public ViewHolder(View view, RecyclerViewInterface recyclerViewInterface){
            super(view);
            avatar = view.findViewById(R.id.Avatar);
            time = view.findViewById(R.id.Time);
            Title = view.findViewById(R.id.Title);
            subTitle = view.findViewById(R.id.subTitle);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    recyclerViewInterface.onItemClick(getAdapterPosition());
                }
            });
        }

    }
}
