package com.example.chatapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MediaTest {
    @Test
    public void sizesAreHumanReadable() {
        assertEquals("850 B", Media.size(850));
        assertEquals("12 KB", Media.size(12 * 1024));
        assertEquals("3.5 MB", Media.size(3_670_016));
    }

    @Test
    public void durationsAreMinutesAndSeconds() {
        assertEquals("0:00", Media.clock(0));
        assertEquals("0:04", Media.clock(4200));
        assertEquals("2:30", Media.clock(150_000));
    }

    @Test
    public void downsamplingKeepsThePictureAtLeastAsLargeAsAsked() {
        assertEquals(1, Media.sampleSize(800, 600, 800));
        assertEquals(1, Media.sampleSize(1000, 700, 800));    // halving would go below 800
        assertEquals(2, Media.sampleSize(1600, 1200, 800));
        assertEquals(4, Media.sampleSize(4000, 3000, 1000));
        assertEquals(8, Media.sampleSize(4000, 3000, 500));
    }
}
