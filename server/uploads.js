// File storage: images, voice notes, documents and profile pictures.
// A file is uploaded first (raw request body, so no multipart parser is needed) and then attached to a message
// or a profile by the name the server returned. Only the uploader can attach it, and only once.
const fs = require("fs"), path = require("path"), crypto = require("crypto"), express = require("express");

const MAX_BYTES = 15 * 1024 * 1024;
const MIME = /^[a-z]+\/[a-z0-9.+-]+$/;
const PICTURE = /^image\/(jpeg|png|webp|gif)$/;
const NAME = /^[a-f0-9]{32}(\.[a-z0-9]{1,8})?$/;

module.exports = function uploads({ app, dbc, requireAuth }) {
    const db = dbc.promise();
    const dir = path.resolve(process.env.UPLOAD_DIR || path.join(__dirname, "..", "uploads"));
    fs.mkdirSync(dir, { recursive: true });

    // POST /upload?name=<original file name>, Content-Type = the file's type, body = the file
    app.post("/upload", requireAuth, express.raw({ type: () => true, limit: MAX_BYTES }), async (req, res) => {
        const mime = String(req.headers["content-type"] || "").split(";")[0].trim().toLowerCase();
        if (!MIME.test(mime)) { res.json({ error: "invalid content type" }); return; }
        if (!Buffer.isBuffer(req.body) || req.body.length === 0) { res.json({ error: "empty file" }); return; }
        const originalName = (String(req.query.name || "file").replace(/[\\/\0]/g, "_").trim() || "file").slice(0, 255);
        const ext = (path.extname(originalName).slice(1).toLowerCase().match(/^[a-z0-9]{1,8}$/) || [""])[0];
        const name = crypto.randomBytes(16).toString("hex") + (ext ? "." + ext : "");
        try {
            await fs.promises.writeFile(path.join(dir, name), req.body, { flag: "wx" });
            await db.query("INSERT INTO uploads SET ?", { name, owner: req.userID, mime, size: req.body.length, originalName, createdAt: new Date() });
            res.json({ attachment: name, mime, size: req.body.length });
        } catch (e) {
            console.error(e);
            res.json({ error: "upload failed" });
        }
    });

    // The uploader, the participants of a conversation the file was sent in, and anyone who can see the profile
    // picture may download it; nobody else.
    async function mayRead(name, userID) {
        const [rows] = await db.query(`
            SELECT 1 FROM uploads up WHERE up.name = ? AND (
                up.owner = ?
                OR EXISTS (SELECT 1 FROM users WHERE avatar = up.name)
                OR EXISTS (SELECT 1 FROM messages m WHERE m.attachment = up.name AND (
                        m.sender = ? OR m.receiver = ?
                        OR m.groupID IN (SELECT groupID FROM group_members WHERE userID = ?)))
            )`, [name, userID, userID, userID, userID]);
        return rows.length > 0;
    }

    app.get("/files/:name", requireAuth, async (req, res) => {
        const name = req.params.name;
        if (!NAME.test(name)) { res.status(404).json({ error: "not found" }); return; }
        try {
            if (!(await mayRead(name, req.userID))) { res.status(404).json({ error: "not found" }); return; }
            const [[file]] = await db.query("SELECT mime, originalName FROM uploads WHERE name = ?", [name]);
            const inline = PICTURE.test(file.mime) || /^audio\//.test(file.mime);
            res.set({
                "Content-Type": file.mime,
                "Content-Disposition": `${inline ? "inline" : "attachment"}; filename*=UTF-8''${encodeURIComponent(file.originalName)}`,
                // an uploaded file must never run as a web page
                "X-Content-Type-Options": "nosniff",
                "Content-Security-Policy": "sandbox; default-src 'none'",
                "Cache-Control": "private, max-age=86400, immutable"
            });
            res.sendFile(path.join(dir, name), err => { if (err && !res.headersSent) res.status(404).json({ error: "not found" }); });
        } catch (e) {
            console.error(e);
            res.status(500).json({ error: "error" });
        }
    });

    // sets the profile picture to an uploaded image ({ attachment }) or removes it ({ attachment: null })
    app.post("/me/avatar", requireAuth, async (req, res) => {
        try {
            const name = req.body.attachment;
            if (name === null) {
                await db.query("UPDATE users SET avatar = NULL WHERE userID = ?", [req.userID]);
                res.json({ avatar: null });
                return;
            }
            if (typeof name !== "string") { res.json({ error: "invalid parameters" }); return; }
            const [rows] = await db.query("SELECT mime FROM uploads WHERE name = ? AND owner = ? AND used = 0", [name, req.userID]);
            if (rows.length === 0) { res.json({ error: "unknown attachment" }); return; }
            if (!PICTURE.test(rows[0].mime)) { res.json({ error: "the profile picture must be a JPEG, PNG, WebP or GIF image" }); return; }
            await db.query("UPDATE uploads SET used = 1 WHERE name = ?", [name]);
            await db.query("UPDATE users SET avatar = ? WHERE userID = ?", [name, req.userID]);
            res.json({ avatar: name });
        } catch (e) {
            console.error(e);
            res.json({ error: "" + (e.code || "unknown error") });
        }
    });
};
