// Group chats: create, rename, add and remove members, leave. Messages of a group go through messaging.js.
const MAX_MEMBERS = 50;

module.exports = function groups({ app, dbc, requireAuth, emitToUsers, isOnline, mustBeIntegers }) {
    const db = dbc.promise();
    const fail = (res, e) => { console.error(e); res.json({ error: "" + (e.code || "unknown error") }); };

    const membersOf = async groupID =>
        (await db.query("SELECT userID, role FROM group_members WHERE groupID = ? ORDER BY joinedAt, userID", [groupID]))[0];
    const tell = (ids, groupID, extra = {}) => emitToUsers(ids, "group updated", JSON.stringify({ groupID, ...extra }));

    // loads the group and checks that the caller belongs to it (and is an admin when `admin` is true)
    async function loadGroup(req, res, admin) {
        const groupID = Number(req.body.groupID);
        if (!Number.isInteger(groupID)) { res.json({ error: "invalid parameters" }); return null; }
        const members = await membersOf(groupID);
        const me = members.find(m => m.userID === req.userID);
        if (!me) { res.status(403).json({ error: "forbidden" }); return null; }
        if (admin && me.role !== "admin") { res.status(403).json({ error: "only an admin can do this" }); return null; }
        return { groupID, members, me };
    }
    const cleanName = name => typeof name === "string" && name.trim().length >= 1 && name.trim().length <= 40 ? name.trim() : null;

    app.post("/groups/create", requireAuth, async (req, res) => {
        const name = cleanName(req.body.name);
        if (name === null) { res.json({ error: "the group name must be 1 to 40 characters" }); return; }
        if (!Array.isArray(req.body.members)) { res.json({ error: "invalid parameters" }); return; }
        const others = [...new Set(req.body.members.map(Number))].filter(id => Number.isInteger(id) && id !== req.userID);
        if (others.length === 0) { res.json({ error: "add at least one other member" }); return; }
        if (others.length + 1 > MAX_MEMBERS) { res.json({ error: `a group is limited to ${MAX_MEMBERS} members` }); return; }
        try {
            const [found] = await db.query("SELECT userID FROM users WHERE userID IN (?)", [others]);
            if (found.length !== others.length) { res.json({ error: "unknown member" }); return; }
            const conn = await db.getConnection();
            let groupID;
            try {
                await conn.beginTransaction();
                const now = new Date();
                groupID = (await conn.query("INSERT INTO chat_groups SET ?", { name, createdBy: req.userID, createdAt: now }))[0].insertId;
                await conn.query("INSERT INTO group_members (groupID, userID, role, joinedAt) VALUES ?",
                    [[[groupID, req.userID, "admin", now], ...others.map(id => [groupID, id, "member", now])]]);
                await conn.commit();
            } catch (e) { await conn.rollback(); throw e; } finally { conn.release(); }
            tell([req.userID, ...others], groupID);
            res.json({ groupID });
        } catch (e) { fail(res, e); }
    });

    app.post("/groups/info", requireAuth, mustBeIntegers("groupID"), async (req, res) => {
        try {
            const g = await loadGroup(req, res, false);
            if (!g) return;
            const [[group]] = await db.query("SELECT groupID, name, createdAt FROM chat_groups WHERE groupID = ?", [g.groupID]);
            const [members] = await db.query(
                `SELECT u.userID, u.userName, u.avatar, gm.role FROM group_members gm JOIN users u ON u.userID = gm.userID
                 WHERE gm.groupID = ? ORDER BY gm.joinedAt, u.userID`, [g.groupID]);
            res.json({ group: { ...group, me: g.me.role, members: members.map(m => ({ ...m, online: isOnline(m.userID) })) } });
        } catch (e) { fail(res, e); }
    });

    app.post("/groups/rename", requireAuth, mustBeIntegers("groupID"), async (req, res) => {
        try {
            const g = await loadGroup(req, res, true);
            if (!g) return;
            const name = cleanName(req.body.name);
            if (name === null) { res.json({ error: "the group name must be 1 to 40 characters" }); return; }
            await db.query("UPDATE chat_groups SET name = ? WHERE groupID = ?", [name, g.groupID]);
            tell(g.members.map(m => m.userID), g.groupID);
            res.json({ name });
        } catch (e) { fail(res, e); }
    });

    app.post("/groups/add", requireAuth, mustBeIntegers("groupID", "userID"), async (req, res) => {
        try {
            const g = await loadGroup(req, res, true);
            if (!g) return;
            const userID = Number(req.body.userID);
            if (g.members.length >= MAX_MEMBERS) { res.json({ error: `a group is limited to ${MAX_MEMBERS} members` }); return; }
            if ((await db.query("SELECT 1 FROM users WHERE userID = ?", [userID]))[0].length === 0) { res.json({ error: "unknown member" }); return; }
            if (g.members.some(m => m.userID === userID)) { res.json({ error: "already a member" }); return; }
            // a new member starts with everything already read: the history before they joined is not "new" to them
            await db.query(`INSERT INTO group_members (groupID, userID, role, joinedAt, lastReadID)
                            VALUES (?, ?, 'member', ?, (SELECT COALESCE(MAX(messageID), 0) FROM messages WHERE groupID = ?))`,
                [g.groupID, userID, new Date(), g.groupID]);
            tell([...g.members.map(m => m.userID), userID], g.groupID);
            res.json({ added: userID });
        } catch (e) { fail(res, e); }
    });

    // an admin removes someone, or anyone leaves by removing themselves
    app.post("/groups/remove", requireAuth, mustBeIntegers("groupID", "userID"), async (req, res) => {
        try {
            const g = await loadGroup(req, res, false);
            if (!g) return;
            const userID = Number(req.body.userID);
            if (userID !== req.userID && g.me.role !== "admin") { res.status(403).json({ error: "only an admin can remove a member" }); return; }
            if (!g.members.some(m => m.userID === userID)) { res.json({ error: "not a member" }); return; }
            await db.query("DELETE FROM group_members WHERE groupID = ? AND userID = ?", [g.groupID, userID]);
            const rest = g.members.filter(m => m.userID !== userID);
            if (rest.length === 0) {
                await db.query("DELETE FROM chat_groups WHERE groupID = ?", [g.groupID]);   // the messages go with it (cascade)
            } else if (!rest.some(m => m.role === "admin")) {
                await db.query("UPDATE group_members SET role = 'admin' WHERE groupID = ? AND userID = ?", [g.groupID, rest[0].userID]);
            }
            tell(rest.map(m => m.userID), g.groupID);
            tell([userID], g.groupID, { removed: true });
            res.json({ removed: userID });
        } catch (e) { fail(res, e); }
    });
};
