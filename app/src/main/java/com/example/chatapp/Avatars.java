package com.example.chatapp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;

/**
 * Avatars without pictures: the first letter of the name on a colour that is always the same for the same name,
 * so a person is recognisable at a glance in the lists, the conversation header and the profile.
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

    /** Fills a circular TextView (style ChatApp.Avatar). */
    public static void apply(TextView view, String name) {
        view.setText(initial(name));
        ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(color(view.getContext(), name)));
    }

    /** Same, for a MaterialButton used as an avatar. */
    public static void apply(MaterialButton button, String name) {
        button.setText(initial(name));
        button.setBackgroundTintList(ColorStateList.valueOf(color(button.getContext(), name)));
    }
}
