package com.example.chatapp;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.io.File;

/** Records a voice message from the microphone as AAC in an .m4a file in the cache folder. */
public final class VoiceRecorder {
    public static final long MAX_MS = 10 * 60 * 1000;   // the server refuses longer messages

    public static class Result {
        public final File file;
        public final long durationMs;

        Result(File file, long durationMs) {
            this.file = file;
            this.durationMs = durationMs;
        }
    }

    private MediaRecorder recorder;
    private File file;
    private long startedAt;

    /** False when the microphone cannot be opened. */
    @SuppressWarnings("deprecation")
    public boolean start(Context context) {
        try {
            file = File.createTempFile("voice", ".m4a", context.getCacheDir());
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64_000);
            recorder.setAudioSamplingRate(44_100);
            recorder.setOutputFile(file.getPath());
            recorder.prepare();
            recorder.start();
            startedAt = SystemClock.elapsedRealtime();
            return true;
        } catch (Exception e) {
            cancel();
            return false;
        }
    }

    /** The loudest sound since the last call, 0..32767 (0 when not recording). */
    public int amplitude() {
        try {
            return recorder == null ? 0 : recorder.getMaxAmplitude();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public long elapsed() {
        return recorder == null ? 0 : SystemClock.elapsedRealtime() - startedAt;
    }

    /** Ends the recording; null when nothing usable was recorded. */
    @Nullable
    public Result stop() {
        if (recorder == null) return null;
        long duration = elapsed();
        try {
            recorder.stop();
        } catch (RuntimeException e) {   // stopped right after starting: no valid data
            cancel();
            return null;
        }
        recorder.release();
        recorder = null;
        return new Result(file, duration);
    }

    /** Throws the recording away. */
    public void cancel() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                // nothing recorded yet
            }
            recorder.release();
            recorder = null;
        }
        if (file != null) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            file = null;
        }
    }
}
