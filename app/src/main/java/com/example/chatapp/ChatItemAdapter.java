package com.example.chatapp;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The messages of a conversation: text, photos, voice messages and files, with replies and read ticks. */
public class ChatItemAdapter extends RecyclerView.Adapter<ChatItemAdapter.ViewHolder> {

    public interface Actions {
        void onLongClick(ChatItem item, View bubble);
        void onReplyClick(int messageID);
        void onImageClick(ChatItem item);
        void onFileClick(ChatItem item);
        void onAudioClick(ChatItem item);
    }

    private static final Object PLAYBACK = new Object();   // payload: only the voice message row changed
    private static final int TICK_READ = 0xFF5EEAD4;       // teal: the message was read

    private final ArrayList<ChatItem> list = new ArrayList<>();
    private final Actions actions;
    private final int myID;
    private boolean group;
    private int playingID = -1;
    private boolean playing;
    private float playProgress;

    public ChatItemAdapter(int myID, Actions actions) {
        this.myID = myID;
        this.actions = actions;
    }

    public List<ChatItem> getList() {
        return list;
    }

    /** In a group chat the name of the sender is shown above each message. */
    public void setGroup(boolean group) {
        this.group = group;
    }

    public void setList(List<ChatItem> items) {
        list.clear();
        list.addAll(items);
        notifyDataSetChanged();
    }

    public void add(ChatItem item) {
        list.add(item);
        notifyItemInserted(list.size() - 1);
    }

    public void prepend(List<ChatItem> items) {
        list.addAll(0, items);
        notifyItemRangeInserted(0, items.size());
    }

    public int indexOf(int messageID) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).getMessageID() == messageID) return i;
        return -1;
    }

    public void replace(int index, ChatItem item) {
        list.set(index, item);
        notifyItemChanged(index);
    }

    public void changed(int index) {
        notifyItemChanged(index);
    }

    /** Which voice message is playing, how far, and whether it is paused. */
    public void setPlayback(int messageID, boolean playing, float progress) {
        int previous = playingID;
        playingID = messageID;
        this.playing = playing;
        playProgress = progress;
        if (previous != -1 && previous != messageID) {
            int index = indexOf(previous);
            if (index >= 0) notifyItemChanged(index, PLAYBACK);
        }
        int index = indexOf(messageID);
        if (index >= 0) notifyItemChanged(index, PLAYBACK);
    }

    @Override
    public int getItemViewType(int position) {
        return list.get(position).getType();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // your own messages on the right, the other person's on the left
        int layout = viewType == ChatItem.SENDER ? R.layout.chat_item_right : R.layout.chat_item;
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PLAYBACK)) {
            bindAudio(holder, list.get(position));
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatItem item = list.get(position);
        boolean mine = item.getType() == ChatItem.SENDER;
        boolean deleted = item.isDeleted();
        String kind = item.getMsgType();

        // sender name (groups, other people's messages)
        boolean showName = group && !mine && item.getSenderName() != null;
        holder.header.setVisibility(showName ? View.VISIBLE : View.GONE);
        if (showName) {
            holder.header.setText(item.getSenderName());
            holder.header.setTextColor(Avatars.color(holder.itemView.getContext(), item.getSenderName()));
        }

        // the quoted message
        ChatItem.Reply reply = item.getReply();
        boolean showReply = reply != null && !deleted;
        holder.replyBlock.setVisibility(showReply ? View.VISIBLE : View.GONE);
        if (showReply) {
            holder.replyName.setText(reply.sender == myID ? "You" : reply.senderName);
            holder.replyText.setText(ChatItem.summary(reply.type, reply.content, null, reply.deleted));
            holder.replyBlock.setOnClickListener(v -> actions.onReplyClick(reply.messageID));
        }

        // photo
        boolean showImage = !deleted && ChatItem.IMAGE.equals(kind) && item.getAttachment() != null;
        holder.image.setVisibility(showImage ? View.VISIBLE : View.GONE);
        holder.image.setImageDrawable(null);
        holder.image.setTag(showImage ? item.getAttachment() : null);
        if (showImage) {
            String attachment = item.getAttachment();
            Media.image(holder.itemView.getContext(), attachment, 640, bitmap -> {
                if (bitmap != null && attachment.equals(holder.image.getTag())) holder.image.setImageBitmap(bitmap);
            });
            holder.image.setOnClickListener(v -> actions.onImageClick(item));
        }

        // voice message
        boolean showAudio = !deleted && ChatItem.AUDIO.equals(kind) && item.getAttachment() != null;
        holder.audioRow.setVisibility(showAudio ? View.VISIBLE : View.GONE);
        if (showAudio) {
            bindAudio(holder, item);
            holder.audioPlay.setOnClickListener(v -> actions.onAudioClick(item));
        }

        // document
        boolean showFile = !deleted && ChatItem.FILE.equals(kind) && item.getAttachment() != null;
        holder.fileRow.setVisibility(showFile ? View.VISIBLE : View.GONE);
        if (showFile) {
            holder.fileName.setText(item.getFileName());
            holder.fileSize.setText(Media.size(item.getFileSize()));
            holder.fileRow.setOnClickListener(v -> actions.onFileClick(item));
        }

        // text (or the caption of a photo)
        boolean showBody = deleted || !item.getMessage().isEmpty();
        holder.body.setVisibility(showBody ? View.VISIBLE : View.GONE);
        holder.body.setText(deleted ? "This message was deleted" : item.getMessage());
        holder.body.setTypeface(null, deleted ? Typeface.ITALIC : Typeface.NORMAL);
        holder.body.setAlpha(deleted ? 0.7f : 1f);

        // time, "edited", and the ticks of your own messages
        SpannableStringBuilder footer = new SpannableStringBuilder();
        if (item.isEdited() && !deleted) footer.append("edited · ");
        footer.append(TimeFormat.bubble(item.getDate()));
        if (mine && !deleted) {
            boolean read = item.isRead() && !group;
            int start = footer.length();
            footer.append(read ? " ✓✓" : " ✓");
            if (read) footer.setSpan(new ForegroundColorSpan(TICK_READ), start, footer.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        holder.footer.setText(footer);

        // the parts that react to a tap must also open the menu on a long press, or they would swallow it
        View.OnLongClickListener menu = v -> {
            actions.onLongClick(item, holder.bubble);
            return true;
        };
        holder.bubble.setOnLongClickListener(menu);
        holder.image.setOnLongClickListener(menu);
        holder.fileRow.setOnLongClickListener(menu);
        holder.replyBlock.setOnLongClickListener(menu);
        holder.audioPlay.setOnLongClickListener(menu);
    }

    private void bindAudio(ViewHolder holder, ChatItem item) {
        boolean current = item.getMessageID() == playingID;
        holder.audioPlay.setImageResource(current && playing ? R.drawable.ic_pause : R.drawable.ic_play);
        holder.audioWave.setBars(item.getWaveform());
        holder.audioWave.setProgress(current && (playing || playProgress > 0) ? playProgress : -1f);
        long duration = item.getDuration();
        holder.audioTime.setText(current && playProgress > 0
                ? Media.clock((long) (duration * playProgress)) + " / " + Media.clock(duration)
                : Media.clock(duration));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final View bubble, replyBlock, audioRow, fileRow;
        final TextView body, header, footer, replyName, replyText, audioTime, fileName, fileSize;
        final ImageView image;
        final ImageButton audioPlay;
        final WaveformView audioWave;

        public ViewHolder(View view) {
            super(view);
            bubble = view.findViewById(R.id.bubble);
            body = view.findViewById(R.id.body);
            header = view.findViewById(R.id.header);
            footer = view.findViewById(R.id.footer);
            replyBlock = view.findViewById(R.id.reply_block);
            replyName = view.findViewById(R.id.reply_name);
            replyText = view.findViewById(R.id.reply_text);
            image = view.findViewById(R.id.image);
            audioRow = view.findViewById(R.id.audio_row);
            audioPlay = view.findViewById(R.id.audio_play);
            audioWave = view.findViewById(R.id.audio_wave);
            audioTime = view.findViewById(R.id.audio_time);
            fileRow = view.findViewById(R.id.file_row);
            fileName = view.findViewById(R.id.file_name);
            fileSize = view.findViewById(R.id.file_size);
        }
    }
}
