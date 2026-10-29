// Messages: sending (text, image, voice note, file), replies, edit, delete, read receipts, history and the
// conversation list. Direct messages and group messages share one table; a group message has `groupID` and no `receiver`.
const TYPES = ["text", "image", "audio", "file"];
const MAX_TEXT = 4000, MAX_CAPTION = 1000, MAX_AUDIO_MS = 10 * 60 * 1000, PAGE = 30, MAX_PAGE = 100;
const MIME_OF = { image: /^image\/(jpeg|png|webp|gif)$/, audio: /^audio\//, file: /^[\w.+-]+\/[\w.+-]+$/ };

module.exports = function messaging({ app, dbc, requireAuth, emitToUsers, isOnline, mustBeIntegers }) {
    const db = dbc.promise();
    const bad = error => ({ error });
    const iso = value => { const t = new Date(value); return isNaN(t) ? null : t.toISOString(); };

    // the row as the clients see it: a deleted message keeps its place in the conversation but loses its content
    function present(row) {
        const deleted = row.deletedAt != null;
        const message = {
            messageID: row.messageID,
            sender: row.sender,
            receiver: row.receiver,
            groupID: row.groupID,
            type: row.type,
            content: deleted ? "" : row.content,
            attachment: deleted ? null : row.attachment,
            fileName: deleted ? null : row.fileName,
            fileSize: deleted ? null : row.fileSize,
            duration: row.duration,
            waveform: row.waveform ? row.waveform.split(",").map(Number) : null,
            replyTo: row.replyTo,
            edited: row.editedAt != null,
            deleted,
            readAt: row.readAt ? iso(row.readAt) : null,
            date: iso(row.date)
        };
        if (row.senderName !== undefined) message.senderName = row.senderName;
        if (row.replyTo != null && row.replySender !== undefined && row.replySender !== null) {
            const replyDeleted = row.replyDeletedAt != null;
            message.reply = {
                messageID: row.replyTo, sender: row.replySender, senderName: row.replySenderName,
                type: row.replyType, content: replyDeleted ? "" : row.replyContent, deleted: replyDeleted
            };
        }
        return message;
    }

    const SELECT_MESSAGE = `SELECT m.*, su.userName AS senderName,
            r.sender AS replySender, r.content AS replyContent, r.type AS replyType, r.deletedAt AS replyDeletedAt, ru.userName AS replySenderName
        FROM messages m
        JOIN users su ON su.userID = m.sender
        LEFT JOIN messages r ON r.messageID = m.replyTo
        LEFT JOIN users ru ON ru.userID = r.sender`;

    async function fetchMessage(id) {
        const [rows] = await db.query(SELECT_MESSAGE + " WHERE m.messageID = ?", [id]);
        return rows[0];
    }

    // the users that have to see (and be told about) a message
    async function audience(row) {
        if (row.groupID != null) {
            const [members] = await db.query("SELECT userID FROM group_members WHERE groupID = ?", [row.groupID]);
            return members.map(m => m.userID);
        }
        return [row.sender, row.receiver];
    }

    const isMember = async (groupID, userID) =>
        (await db.query("SELECT 1 FROM group_members WHERE groupID = ? AND userID = ?", [groupID, userID]))[0].length > 0;

    // ---- sending -----------------------------------------------------------------------------------------------
    // `userID` comes from the token. Returns { message } or { error }.
    async function createMessage(userID, data) {
        if (data === null || typeof data !== "object") return bad("invalid parameters");
        const type = data.type === undefined ? "text" : data.type;
        if (!TYPES.includes(type)) return bad("invalid message type");

        const hasReceiver = data.receiver !== undefined && data.receiver !== null;
        const hasGroup = data.groupID !== undefined && data.groupID !== null;
        if (hasReceiver === hasGroup) return bad("a message needs exactly one of receiver and groupID");
        const receiver = hasReceiver ? Number(data.receiver) : null, groupID = hasGroup ? Number(data.groupID) : null;
        if (!Number.isInteger(hasReceiver ? receiver : groupID)) return bad("invalid parameters");

        const content = data.content === undefined || data.content === null ? "" : data.content;
        if (typeof content !== "string") return bad("invalid parameters");
        if (type === "text") {
            if (content.trim().length === 0) return bad("a message cannot be empty");
            if (content.length > MAX_TEXT) return bad(`a message is limited to ${MAX_TEXT} characters`);
        } else if (content.length > MAX_CAPTION) {
            return bad(`a caption is limited to ${MAX_CAPTION} characters`);
        }

        if (hasReceiver) {
            if (receiver === userID) return bad("you cannot send a message to yourself");
            const [users] = await db.query("SELECT 1 FROM users WHERE userID = ?", [receiver]);
            if (users.length === 0) return bad("unknown receiver");
        } else if (!(await isMember(groupID, userID))) {
            return bad("you are not a member of this group");
        }

        let replyTo = null;
        if (data.replyTo !== undefined && data.replyTo !== null) {
            replyTo = Number(data.replyTo);
            if (!Number.isInteger(replyTo)) return bad("invalid parameters");
            const target = (await db.query("SELECT sender, receiver, groupID FROM messages WHERE messageID = ?", [replyTo]))[0][0];
            const sameConversation = target && (hasGroup
                ? target.groupID === groupID
                : target.groupID === null && [target.sender, target.receiver].sort().join() === [userID, receiver].sort().join());
            if (!sameConversation) return bad("the message you reply to is not in this conversation");
        }

        const row = { sender: userID, receiver, groupID, content, type, replyTo, date: new Date() };
        let claimed = null;
        if (type !== "text") {
            if (typeof data.attachment !== "string") return bad("missing attachment");
            // the file must be one this user uploaded and has not attached yet
            const [uploads] = await db.query("SELECT * FROM uploads WHERE name = ? AND owner = ? AND used = 0", [data.attachment, userID]);
            if (uploads.length === 0) return bad("unknown attachment");
            if (!MIME_OF[type].test(uploads[0].mime)) return bad(`the attachment is not ${type === "file" ? "a valid file" : "an " + type}`);
            const [claim] = await db.query("UPDATE uploads SET used = 1 WHERE name = ? AND used = 0", [data.attachment]);
            if (claim.affectedRows !== 1) return bad("unknown attachment");
            claimed = data.attachment;
            row.attachment = data.attachment;
            row.fileName = uploads[0].originalName;
            row.fileSize = uploads[0].size;
            if (type === "audio") {
                const duration = Number(data.duration);
                row.duration = Number.isInteger(duration) && duration >= 0 && duration <= MAX_AUDIO_MS ? duration : null;
                // the loudness of the recording, drawn as bars by the apps: at most 100 whole numbers from 0 to 100
                const wave = data.waveform;
                if (Array.isArray(wave) && wave.length > 0 && wave.length <= 100 && wave.every(v => Number.isInteger(v) && v >= 0 && v <= 100)) {
                    row.waveform = wave.join(",");
                }
            }
        }

        let id;
        try {
            id = (await db.query("INSERT INTO messages SET ?", row))[0].insertId;
        } catch (e) {
            if (claimed) await db.query("UPDATE uploads SET used = 0 WHERE name = ?", [claimed]);
            console.error("failed to add message to DB: " + e.code);
            return bad("" + e.code);
        }
        const stored = await fetchMessage(id);
        const message = present(stored);
        emitToUsers(await audience(stored), "chat message", JSON.stringify(message));
        console.info(`message added. [messageID = ${id}, type = ${type}]`);
        return { message };
    }

    app.post("/messages/send", requireAuth, (req, res) => {
        // the sender is the token's user; naming someone else is refused
        if (req.body.sender !== undefined && Number(req.body.sender) !== req.userID) { res.status(403).json({ error: "forbidden" }); return; }
        createMessage(req.userID, req.body).then(result => {
            if (result.error) res.json({ error: result.error });
            else res.json({ messageID: result.message.messageID, message: result.message });
        }).catch(e => { console.error(e); res.json({ error: "" + (e.code || "unknown error") }); });
    });

    // ---- history -----------------------------------------------------------------------------------------------
    // The latest `limit` messages (30 by default), oldest first; `before` (a messageID) asks for the page before it.
    async function history(res, where, params, body) {
        const limit = Math.min(Math.max(parseInt(body.limit, 10) || PAGE, 1), MAX_PAGE);
        const before = body.before !== undefined && body.before !== null ? Number(body.before) : null;
        if (before !== null && !Number.isInteger(before)) { res.json({ error: "invalid parameters" }); return; }
        try {
            const [rows] = await db.query(
                `${SELECT_MESSAGE} WHERE ${where} ${before !== null ? "AND m.messageID < ?" : ""} ORDER BY m.messageID DESC LIMIT ?`,
                [...params, ...(before !== null ? [before] : []), limit + 1]);
            const hasMore = rows.length > limit;
            res.json({ messages: rows.slice(0, limit).reverse().map(present), hasMore });
        } catch (e) {
            res.json({ error: "" + e.code });
        }
    }

    app.post("/messages", requireAuth, mustBeIntegers("userID1", "userID2"), (req, res) => {
        const a = Number(req.body.userID1), b = Number(req.body.userID2);
        if (a !== req.userID && b !== req.userID) { res.status(403).json({ error: "forbidden" }); return; }
        history(res, "m.groupID IS NULL AND ((m.sender = ? AND m.receiver = ?) OR (m.sender = ? AND m.receiver = ?))", [a, b, b, a], req.body);
    });

    app.post("/groups/messages", requireAuth, mustBeIntegers("groupID"), async (req, res) => {
        const groupID = Number(req.body.groupID);
        if (!(await isMember(groupID, req.userID))) { res.status(403).json({ error: "forbidden" }); return; }
        history(res, "m.groupID = ?", [groupID], req.body);
    });

    // ---- edit and delete (only your own messages) ---------------------------------------------------------------
    async function ownMessage(req, res) {
        const id = Number(req.body.messageID);
        if (!Number.isInteger(id)) { res.json({ error: "invalid parameters" }); return null; }
        const row = await fetchMessage(id);
        if (!row) { res.json({ error: "unknown message" }); return null; }
        if (row.sender !== req.userID) { res.status(403).json({ error: "forbidden" }); return null; }
        if (row.deletedAt) { res.json({ error: "the message was deleted" }); return null; }
        return row;
    }
    const announce = async id => {
        const row = await fetchMessage(id), message = present(row);
        emitToUsers(await audience(row), "message updated", JSON.stringify(message));
        return message;
    };

    app.post("/messages/edit", requireAuth, async (req, res) => {
        try {
            const row = await ownMessage(req, res);
            if (!row) return;
            const content = req.body.content;
            if (row.type !== "text") { res.json({ error: "only text messages can be edited" }); return; }
            if (typeof content !== "string" || content.trim().length === 0 || content.length > MAX_TEXT) { res.json({ error: "invalid content" }); return; }
            await db.query("UPDATE messages SET content = ?, editedAt = ? WHERE messageID = ?", [content, new Date(), row.messageID]);
            res.json({ message: await announce(row.messageID) });
        } catch (e) { console.error(e); res.json({ error: "" + (e.code || "unknown error") }); }
    });

    app.post("/messages/delete", requireAuth, async (req, res) => {
        try {
            const row = await ownMessage(req, res);
            if (!row) return;
            await db.query("UPDATE messages SET deletedAt = ? WHERE messageID = ?", [new Date(), row.messageID]);
            res.json({ message: await announce(row.messageID) });
        } catch (e) { console.error(e); res.json({ error: "" + (e.code || "unknown error") }); }
    });

    // ---- read receipts ------------------------------------------------------------------------------------------
    // { userID: <the other person> } or { groupID }: everything addressed to you in that conversation is now read.
    app.post("/messages/read", requireAuth, async (req, res) => {
        try {
            if (req.body.groupID !== undefined) {
                const groupID = Number(req.body.groupID);
                if (!Number.isInteger(groupID) || !(await isMember(groupID, req.userID))) { res.status(403).json({ error: "forbidden" }); return; }
                await db.query(`UPDATE group_members SET lastReadID = (SELECT COALESCE(MAX(messageID), 0) FROM messages WHERE groupID = ?)
                                WHERE groupID = ? AND userID = ?`, [groupID, groupID, req.userID]);
                res.json({ read: true });
                return;
            }
            const peer = Number(req.body.userID);
            if (!Number.isInteger(peer)) { res.json({ error: "invalid parameters" }); return; }
            const [unread] = await db.query(
                "SELECT messageID FROM messages WHERE groupID IS NULL AND sender = ? AND receiver = ? AND readAt IS NULL", [peer, req.userID]);
            if (unread.length > 0) {
                const ids = unread.map(r => r.messageID);
                await db.query("UPDATE messages SET readAt = ? WHERE messageID IN (?)", [new Date(), ids]);
                emitToUsers([peer], "messages read", JSON.stringify({ by: req.userID, ids }));
            }
            res.json({ read: unread.length });
        } catch (e) { console.error(e); res.json({ error: "" + (e.code || "unknown error") }); }
    });

    // ---- the conversation list ----------------------------------------------------------------------------------
    // One entry per person you talk to and per group you are in, most recent first, with the unread count.
    app.post("/conversations", requireAuth, async (req, res) => {
        const me = req.userID;
        try {
            const [direct] = await db.query(`
                SELECT t.*, u.userName, u.avatar, u.lastSeen FROM (
                    SELECT m.messageID, m.sender, m.content, m.type, m.date, m.deletedAt,
                           IF(m.sender = ?, m.receiver, m.sender) AS peer,
                           ROW_NUMBER() OVER (PARTITION BY IF(m.sender = ?, m.receiver, m.sender) ORDER BY m.messageID DESC) AS rn
                    FROM messages m WHERE m.groupID IS NULL AND (m.sender = ? OR m.receiver = ?)
                ) t JOIN users u ON u.userID = t.peer WHERE t.rn = 1`, [me, me, me, me]);
            const [unread] = await db.query(
                `SELECT sender, COUNT(*) AS n FROM messages
                 WHERE receiver = ? AND groupID IS NULL AND readAt IS NULL AND deletedAt IS NULL GROUP BY sender`, [me]);
            const unreadFrom = new Map(unread.map(r => [r.sender, Number(r.n)]));
            const [groups] = await db.query(`
                SELECT g.groupID, g.name, g.createdAt,
                       (SELECT COUNT(*) FROM group_members x WHERE x.groupID = g.groupID) AS members,
                       l.messageID, l.sender, l.content, l.type, l.date, l.deletedAt, su.userName AS senderName,
                       (SELECT COUNT(*) FROM messages m WHERE m.groupID = g.groupID AND m.messageID > gm.lastReadID
                          AND m.sender <> ? AND m.deletedAt IS NULL) AS unread
                FROM group_members gm
                JOIN chat_groups g ON g.groupID = gm.groupID
                LEFT JOIN messages l ON l.messageID = (SELECT MAX(messageID) FROM messages WHERE groupID = g.groupID)
                LEFT JOIN users su ON su.userID = l.sender
                WHERE gm.userID = ?`, [me, me]);

            const preview = r => r.messageID == null ? null :
                { messageID: r.messageID, sender: r.sender, senderName: r.senderName, type: r.type, content: r.deletedAt ? "" : r.content, deleted: r.deletedAt != null };
            const conversations = [
                ...direct.map(r => ({
                    kind: "user", id: r.peer, name: r.userName, avatar: r.avatar, online: isOnline(r.peer),
                    lastSeen: r.lastSeen ? new Date(r.lastSeen).toISOString() : null,
                    preview: preview(r), date: iso(r.date), unread: unreadFrom.get(r.peer) || 0
                })),
                ...groups.map(r => ({
                    kind: "group", id: r.groupID, name: r.name, members: Number(r.members),
                    preview: preview(r), date: iso(r.date || r.createdAt), unread: Number(r.unread)
                }))
            ].sort((a, b) => b.date.localeCompare(a.date));
            res.json({ conversations });
        } catch (e) { console.error(e); res.json({ error: "" + (e.code || "unknown error") }); }
    });

    return { createMessage, audience, isMember };
};
