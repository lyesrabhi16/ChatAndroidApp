package com.example.chatapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ChatItemTest {
    private static final int ME = 7;

    @Test
    public void yourOwnMessageIsOnTheRightAndReadStateComesFromReadAt() throws Exception {
        JSONObject m = new JSONObject("{\"messageID\":12,\"sender\":7,\"receiver\":9,\"groupID\":null,\"type\":\"text\","
                + "\"content\":\"hi\",\"edited\":true,\"deleted\":false,\"readAt\":\"2026-01-01T10:00:00.000Z\",\"date\":\"2026-01-01T09:59:00.000Z\"}");
        ChatItem item = ChatItem.fromJson(m, ME);
        assertEquals(ChatItem.SENDER, item.getType());
        assertEquals(12, item.getMessageID());
        assertEquals(-1, item.getGroupID());
        assertTrue(item.isEdited());
        assertTrue(item.isRead());
        assertEquals("hi", item.getMessage());
    }

    @Test
    public void anotherPersonsGroupMessageKeepsTheSenderName() throws Exception {
        JSONObject m = new JSONObject("{\"messageID\":3,\"sender\":9,\"groupID\":4,\"senderName\":\"Dana\",\"type\":\"text\",\"content\":\"yo\",\"readAt\":null}");
        ChatItem item = ChatItem.fromJson(m, ME);
        assertEquals(ChatItem.RECEIVER, item.getType());
        assertEquals(4, item.getGroupID());
        assertEquals("Dana", item.getSenderName());
        assertFalse(item.isRead());
    }

    @Test
    public void aReplyCarriesTheQuotedMessage() throws Exception {
        JSONObject m = new JSONObject("{\"messageID\":5,\"sender\":9,\"type\":\"text\",\"content\":\"answer\",\"replyTo\":2,"
                + "\"reply\":{\"messageID\":2,\"sender\":7,\"senderName\":\"me\",\"type\":\"image\",\"content\":\"\",\"deleted\":false}}");
        ChatItem item = ChatItem.fromJson(m, ME);
        assertEquals(2, item.getReplyTo());
        assertNotNull(item.getReply());
        assertEquals("image", item.getReply().type);
        assertEquals("Photo", ChatItem.summary(item.getReply().type, item.getReply().content, null, item.getReply().deleted));
    }

    @Test
    public void attachmentsKeepTheirDetails() throws Exception {
        JSONObject m = new JSONObject("{\"messageID\":8,\"sender\":7,\"type\":\"audio\",\"content\":\"\",\"attachment\":\"abc.m4a\",\"duration\":4200}");
        ChatItem item = ChatItem.fromJson(m, ME);
        assertEquals(ChatItem.AUDIO, item.getMsgType());
        assertEquals("abc.m4a", item.getAttachment());
        assertEquals(4200, item.getDuration());
        assertNull(item.getFileName());
    }

    @Test
    public void summariesDescribeEveryKindOfMessage() {
        assertEquals("hello world", ChatItem.summary("text", "hello\nworld", null, false));
        assertEquals("Photo", ChatItem.summary("image", "", null, false));
        assertEquals("Photo: sunset", ChatItem.summary("image", "sunset", null, false));
        assertEquals("Voice message", ChatItem.summary("audio", "", null, false));
        assertEquals("report.pdf", ChatItem.summary("file", "", "report.pdf", false));
        assertEquals("File", ChatItem.summary("file", "", null, false));
        assertEquals("This message was deleted", ChatItem.summary("text", "secret", null, true));
    }
}
