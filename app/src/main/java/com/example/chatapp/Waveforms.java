package com.example.chatapp;

import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.util.List;
import java.util.Random;

/** The loudness bars of a voice message: measuring them while recording and making up a stand-in for old messages. */
public final class Waveforms {
    private Waveforms() {}

    /** Bars drawn in a message. */
    public static final int BARS = 40;

    /** A microphone amplitude (0..32767) as a bar height from 0 to 1; the square root gives quiet speech a visible height. */
    public static float level(int amplitude) {
        double normal = Math.min(Math.max(amplitude, 0), 32767) / 32767.0;
        return (float) Math.min(1.0, Math.sqrt(normal) * 1.25);
    }

    /** `levels` (0..1, any count) reduced to `count` whole numbers 0..100, each the loudest of its slice. */
    public static JSONArray downsample(List<Float> levels, int count) {
        JSONArray out = new JSONArray();
        int n = levels.size();
        for (int i = 0; i < count; i++) {
            float max = 0;
            if (n > 0) {
                int from = i * n / count;
                int to = Math.max(from + 1, (i + 1) * n / count);
                for (int j = from; j < Math.min(to, n); j++) max = Math.max(max, levels.get(j));
            }
            try {
                out.put(Math.round(max * 100));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return out;
    }

    /** The bars of a message as 0..1 values. */
    public static float[] fromJson(@Nullable JSONArray array) {
        if (array == null || array.length() == 0) return null;
        float[] bars = new float[array.length()];
        for (int i = 0; i < bars.length; i++) bars[i] = Math.min(1f, Math.max(0f, (float) array.optDouble(i, 0) / 100f));
        return bars;
    }

    /** A believable shape for a voice message that was sent without one: always the same for the same message. */
    public static float[] pseudo(long seed, int count) {
        Random random = new Random(seed);
        float[] bars = new float[count];
        float level = 0.5f;
        for (int i = 0; i < count; i++) {
            level = Math.min(1f, Math.max(0.12f, level + (random.nextFloat() - 0.5f) * 0.7f));
            bars[i] = level;
        }
        return bars;
    }
}
