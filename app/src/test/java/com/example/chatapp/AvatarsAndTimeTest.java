package com.example.chatapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

public class AvatarsAndTimeTest {
    private TimeZone original;

    @Before
    public void utc() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    public void theInitialIsTheFirstLetterInCapitals() {
        assertEquals("D", Avatars.initial("dana moreau"));
        assertEquals("É", Avatars.initial("  élodie"));
        assertEquals("?", Avatars.initial("   "));
        assertEquals("?", Avatars.initial(null));
    }

    @Test
    public void serverTimestampsBecomeLocalClockTimes() {
        assertEquals("21:36", TimeFormat.bubble("2026-10-29T21:36:25.000Z"));
        assertEquals("", TimeFormat.bubble(null));
        assertEquals("", TimeFormat.bubble("garbage"));
    }

    @Test
    public void aMissingOrNullJsonStringIsNull() throws Exception {
        JSONObject json = new JSONObject("{\"a\":null,\"b\":\"x\"}");
        assertNull(Api.str(json, "a"));
        assertNull(Api.str(json, "missing"));
        assertEquals("x", Api.str(json, "b"));
    }

    @Test
    public void bodiesSkipNullValues() throws Exception {
        JSONObject body = Api.body("userID", 4, "before", null, "limit", 30);
        assertEquals(4, body.getInt("userID"));
        assertEquals(30, body.getInt("limit"));
        assertEquals(false, body.has("before"));
    }
}
