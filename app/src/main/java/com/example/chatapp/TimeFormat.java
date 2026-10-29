package com.example.chatapp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Turns the server's UTC timestamps (2026-10-29T21:36:25.000Z) into short local times. */
public final class TimeFormat {
    private TimeFormat() {}

    private static Date parse(String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            return in.parse(iso.substring(0, 19));
        } catch (ParseException e) {
            return null;
        }
    }

    private static boolean sameDay(Date a, Date b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTime(a);
        y.setTime(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    private static String clock(Date d) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
    }

    /** Inside a message bubble: just the time. */
    public static String bubble(String iso) {
        Date d = parse(iso);
        return d == null ? "" : clock(d);
    }

    /** In the chat list: the time today, "Yesterday", or the date. */
    public static String list(String iso) {
        Date d = parse(iso);
        if (d == null) return "";
        Date now = new Date();
        if (sameDay(d, now)) return clock(d);
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(d, yesterday.getTime())) return "Yesterday";
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(d);
    }

    /** Under a name in the conversation header: "last seen today at 22:10", "last seen yesterday at 09:30", "last seen 3 Oct". */
    public static String lastSeen(String iso) {
        Date d = parse(iso);
        if (d == null) return "";
        if (sameDay(d, new Date())) return "last seen today at " + clock(d);
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(d, yesterday.getTime())) return "last seen yesterday at " + clock(d);
        return "last seen " + new SimpleDateFormat("d MMM", Locale.getDefault()).format(d);
    }

    /** A message that has just been sent or received. */
    public static String now() {
        return clock(new Date());
    }
}
