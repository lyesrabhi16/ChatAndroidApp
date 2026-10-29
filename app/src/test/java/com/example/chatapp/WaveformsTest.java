package com.example.chatapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WaveformsTest {
    @Test
    public void silenceIsFlatAndLoudSoundFillsTheBar() {
        assertEquals(0f, Waveforms.level(0), 0.0001f);
        assertEquals(1f, Waveforms.level(32767), 0.0001f);
        assertEquals(0f, Waveforms.level(-5), 0.0001f);
        assertTrue("quiet speech must still be visible", Waveforms.level(1000) > 0.15f);
        assertTrue(Waveforms.level(8000) > Waveforms.level(1000));
    }

    @Test
    public void downsamplingKeepsThePeakOfEachSlice() throws Exception {
        List<Float> levels = Arrays.asList(0.1f, 0.9f, 0.2f, 0.3f, 0.0f, 0.5f);
        JSONArray out = Waveforms.downsample(levels, 3);
        assertEquals(3, out.length());
        assertEquals(90, out.getInt(0));   // max of 0.1, 0.9
        assertEquals(30, out.getInt(1));   // max of 0.2, 0.3
        assertEquals(50, out.getInt(2));   // max of 0.0, 0.5
    }

    @Test
    public void aShortRecordingIsStretchedAndAnEmptyOneIsFlat() throws Exception {
        JSONArray stretched = Waveforms.downsample(new ArrayList<>(Arrays.asList(1f, 0f)), 4);
        assertEquals(4, stretched.length());
        JSONArray flat = Waveforms.downsample(new ArrayList<>(), 5);
        for (int i = 0; i < 5; i++) assertEquals(0, flat.getInt(i));
    }

    @Test
    public void barsFromTheServerAreClampedToTheDrawableRange() throws Exception {
        assertArrayEquals(new float[]{0f, 0.5f, 1f}, Waveforms.fromJson(new JSONArray("[0,50,100]")), 0.0001f);
        assertArrayEquals(new float[]{0f, 1f}, Waveforms.fromJson(new JSONArray("[-20,250]")), 0.0001f);
        assertNull(Waveforms.fromJson(new JSONArray("[]")));
        assertNull(Waveforms.fromJson(null));
    }

    @Test
    public void aMessageWithoutBarsGetsTheSameStandInEveryTime() {
        float[] a = Waveforms.pseudo(42, Waveforms.BARS), b = Waveforms.pseudo(42, Waveforms.BARS);
        assertArrayEquals(a, b, 0f);
        assertEquals(Waveforms.BARS, a.length);
        for (float bar : a) assertTrue(bar >= 0.12f && bar <= 1f);
        assertTrue(!Arrays.equals(a, Waveforms.pseudo(43, Waveforms.BARS)));
    }
}
