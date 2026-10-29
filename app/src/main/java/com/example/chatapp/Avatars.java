package com.example.chatapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.view.ViewCompat;

/**
 * Avatars: a profile picture when the person has one, otherwise the first letter of the name on a colour that is
 * always the same for the same name, so a person is recognisable at a glance in the lists, the conversation header
 * and the profile.
 */
public final class Avatars {
    private static final int[] PALETTE = {
            R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
            R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8
    };

    private Avatars() {}

    public static String initial(String name) {
        if (name == null) return "?";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "?";
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).toUpperCase();
    }

    public static int color(Context context, String name) {
        int hash = name == null ? 0 : name.trim().toLowerCase().hashCode();
        return ContextCompat.getColor(context, PALETTE[(hash & 0x7fffffff) % PALETTE.length]);
    }

    /** Fills a circular TextView (style ChatApp.Avatar) with the initial of `name`. */
    public static void apply(TextView view, String name) {
        apply(view, name, null);
    }

    /** Same, with the profile picture `attachment` (a name on the server) once it has loaded. */
    public static void apply(TextView view, String name, String attachment) {
        view.setTag(R.id.avatar_request, attachment);
        view.setBackgroundResource(R.drawable.avatar_circle);   // views are recycled: undo a picture set earlier
        view.setText(initial(name));
        ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(color(view.getContext(), name)));
        if (attachment == null || attachment.isEmpty()) return;
        Media.image(view.getContext(), attachment, 256, bitmap -> {
            // the view may have been reused for someone else while the picture was loading
            if (bitmap == null || !attachment.equals(view.getTag(R.id.avatar_request))) return;
            RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(view.getResources(), bitmap);
            drawable.setCircular(true);
            ViewCompat.setBackgroundTintList(view, null);
            view.setBackground(drawable);
            view.setText("");
        });
    }
}
