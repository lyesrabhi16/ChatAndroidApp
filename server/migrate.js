// Idempotent schema migrations, run every time the server starts.
// chatdb.sql creates the original tables; everything added since then lives here, so an existing database
// (for example a Docker volume created by an older version) is upgraded in place and a fresh one ends up identical.
module.exports = async function migrate(pool) {
    const db = pool.promise();
    const [[{ name: schema }]] = await db.query("SELECT DATABASE() AS name");

    const exists = async (sql, params) => (await db.query(sql, params))[0].length > 0;
    const hasColumn = (table, column) => exists(
        "SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND table_name = ? AND column_name = ?", [schema, table, column]);
    const hasIndex = (table, index) => exists(
        "SELECT 1 FROM information_schema.statistics WHERE table_schema = ? AND table_name = ? AND index_name = ?", [schema, table, index]);
    // returns true when the column was just created
    async function addColumn(table, column, definition) {
        if (await hasColumn(table, column)) return false;
        await db.query(`ALTER TABLE \`${table}\` ADD COLUMN \`${column}\` ${definition}`);
        return true;
    }

    // steps that must run exactly once (data fixes); a step that fails is retried at the next start
    await db.query("CREATE TABLE IF NOT EXISTS schema_steps (name varchar(64) NOT NULL PRIMARY KEY, doneAt datetime NOT NULL) ENGINE=InnoDB");
    async function once(name, step) {
        if ((await db.query("SELECT 1 FROM schema_steps WHERE name = ?", [name]))[0].length > 0) return;
        await step();
        await db.query("INSERT INTO schema_steps (name, doneAt) VALUES (?, UTC_TIMESTAMP())", [name]);
    }

    // ---- users: profile picture and last connection
    await addColumn("users", "avatar", "varchar(64) NULL");
    await addColumn("users", "lastSeen", "datetime NULL");

    // ---- uploads: every file stored on the server, and who may attach it to a message
    await db.query(`CREATE TABLE IF NOT EXISTS uploads (
        name varchar(64) NOT NULL PRIMARY KEY,
        owner int NOT NULL,
        mime varchar(100) NOT NULL,
        size int NOT NULL,
        originalName varchar(255) NOT NULL,
        used tinyint(1) NOT NULL DEFAULT 0,
        createdAt datetime NOT NULL,
        CONSTRAINT FK_uploads_owner FOREIGN KEY (owner) REFERENCES users (userID) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

    // ---- groups
    await db.query(`CREATE TABLE IF NOT EXISTS chat_groups (
        groupID int NOT NULL AUTO_INCREMENT PRIMARY KEY,
        name varchar(40) NOT NULL,
        createdBy int NULL,
        createdAt datetime NOT NULL,
        CONSTRAINT FK_groups_creator FOREIGN KEY (createdBy) REFERENCES users (userID) ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
    await db.query(`CREATE TABLE IF NOT EXISTS group_members (
        groupID int NOT NULL,
        userID int NOT NULL,
        role varchar(10) NOT NULL DEFAULT 'member',
        lastReadID int NOT NULL DEFAULT 0,
        joinedAt datetime NOT NULL,
        PRIMARY KEY (groupID, userID),
        KEY idx_member_user (userID),
        CONSTRAINT FK_member_group FOREIGN KEY (groupID) REFERENCES chat_groups (groupID) ON DELETE CASCADE,
        CONSTRAINT FK_member_user FOREIGN KEY (userID) REFERENCES users (userID) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

    // ---- messages: attachments, replies, edits, deletions, read receipts and group messages
    await db.query("ALTER TABLE messages MODIFY receiver int NULL");   // a group message has no single receiver
    await addColumn("messages", "groupID", "int NULL");
    await addColumn("messages", "type", "varchar(10) NOT NULL DEFAULT 'text'");   // text | image | audio | file
    await addColumn("messages", "attachment", "varchar(64) NULL");
    await addColumn("messages", "fileName", "varchar(255) NULL");
    await addColumn("messages", "fileSize", "int NULL");
    await addColumn("messages", "duration", "int NULL");                           // audio, in milliseconds
    await addColumn("messages", "waveform", "varchar(400) NULL");                  // voice note loudness, comma-separated 0..100
    await addColumn("messages", "replyTo", "int NULL");
    await addColumn("messages", "editedAt", "datetime NULL");
    await addColumn("messages", "deletedAt", "datetime NULL");
    await addColumn("messages", "readAt", "datetime NULL");
    await once("backfill-read-receipts", async () => {
        // the messages that already exist were seen long ago: they must not show up as unread
        await db.query("UPDATE messages SET readAt = UTC_TIMESTAMP() WHERE readAt IS NULL");
    });
    await once("fix-zero-dates", async () => {
        // an old client bug stored 0000-00-00 as a date, which strict MySQL cannot read back
        await db.query("UPDATE messages SET date = '2023-01-01 00:00:00' WHERE CAST(date AS CHAR) = '0000-00-00 00:00:00'");
    });
    if (!(await hasIndex("messages", "idx_messages_group"))) await db.query("CREATE INDEX idx_messages_group ON messages (groupID, messageID)");
    if (!(await hasIndex("messages", "idx_messages_unread"))) await db.query("CREATE INDEX idx_messages_unread ON messages (receiver, readAt)");
    const [fks] = await db.query("SELECT 1 FROM information_schema.table_constraints WHERE table_schema = ? AND table_name = 'messages' AND constraint_name = 'FK_messages_group'", [schema]);
    if (fks.length === 0) {
        await db.query("ALTER TABLE messages ADD CONSTRAINT FK_messages_group FOREIGN KEY (groupID) REFERENCES chat_groups (groupID) ON DELETE CASCADE");
    }
};
