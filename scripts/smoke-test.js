// End-to-end test of the chat server (HTTP API + Socket.IO), including authentication and authorization.
//   docker compose up --build        (in another terminal)
//   npm run smoke
// Environment: API_URL (default http://localhost:8001), SOCKET_URL (default http://localhost:8000)
const { io } = require("socket.io-client");

const API = process.env.API_URL || "http://localhost:8001";
const SOCKET = process.env.SOCKET_URL || "http://localhost:8000";
let failures = 0;

// POST helper: returns { status, body }; `token` is sent as a Bearer token when given.
async function call(path, body, token) {
    const headers = { "Content-Type": "application/json" };
    if (token) headers["Authorization"] = "Bearer " + token;
    const res = await fetch(API + path, { method: "POST", headers, body: JSON.stringify(body) });
    return { status: res.status, body: await res.json() };
}
const post = async (path, body, token) => (await call(path, body, token)).body;

function check(name, condition, detail = "") {
    if (condition) console.log(`  ok    ${name}`);
    else { failures++; console.log(`  FAIL  ${name} ${detail}`); }
}

const sleep = ms => new Promise(r => setTimeout(r, ms));
const b64 = o => Buffer.from(JSON.stringify(o)).toString("base64url");

// Resolves with the connected socket, or rejects with the server's error when the handshake is refused.
function connect(token) {
    return new Promise((resolve, reject) => {
        const socket = io(SOCKET, { transports: ["websocket"], reconnection: false, auth: token ? { token } : {} });
        socket.on("init request", () => { socket.emit("init"); resolve(socket); });
        socket.on("connect_error", e => { socket.close(); reject(e); });
    });
}

(async () => {
    const suffix = Date.now();
    const emailOf = name => `${name}${suffix}@example.com`;

    console.log("accounts");
    const alice = await post("/signup", { userName: "alice", userEmail: emailOf("alice"), userPassword: "pw-alice" });
    const bob = await post("/signup", { userName: "bob", userEmail: emailOf("bob"), userPassword: "pw-bob" });
    const carol = await post("/signup", { userName: "carol", userEmail: emailOf("carol"), userPassword: "pw-carol" });
    check("sign up returns a userID and a token", Number.isInteger(alice.userID) && typeof alice.token === "string", JSON.stringify(alice));
    check("duplicate e-mail is refused", (await post("/signup", { userName: "alice", userEmail: emailOf("alice"), userPassword: "xxxxxx" })).error === "email already exists");
    check("a short password is refused", /userPassword/.test((await post("/signup", { userName: "x", userEmail: emailOf("short"), userPassword: "123" })).error));
    check("a name longer than the column is refused", /userName/.test((await post("/signup", { userName: "n".repeat(30), userEmail: emailOf("long"), userPassword: "pw-long" })).error));
    check("non-string fields are refused", (await post("/signup", { userName: ["a"], userEmail: { x: 1 }, userPassword: 123456 })).error === "invalid parameters");

    const signin = await post("/signin", { userEmail: emailOf("alice"), userPassword: "pw-alice" });
    check("sign in returns the profile and a token", signin.userID === alice.userID && signin.userName === "alice" && typeof signin.token === "string");
    check("the password (or its hash) is never returned", !JSON.stringify(signin).includes("pw-alice") && signin.userPassword === undefined);
    const wrongPassword = await post("/signin", { userEmail: emailOf("alice"), userPassword: "nope" });
    const unknownEmail = await post("/signin", { userEmail: emailOf("nobody"), userPassword: "nope" });
    check("a wrong password is refused", wrongPassword.error === "invalid email or password" && wrongPassword.token === undefined);
    check("an unknown e-mail gives the same answer (no account enumeration)", unknownEmail.error === wrongPassword.error);

    const tA = signin.token, tB = bob.token, tC = carol.token;

    console.log("authentication");
    for (const [route, body] of [["/user", { userID: bob.userID }], ["/search", { searchTable: "users", searchCols: "userName", searchTerm: "bob" }],
        ["/friends", { userID: alice.userID }], ["/friends/add", { userID1: alice.userID, userID2: bob.userID }],
        ["/chats", { userID: alice.userID }], ["/messages", { userID1: alice.userID, userID2: bob.userID }],
        ["/messages/send", { sender: alice.userID, receiver: bob.userID, content: "x", date: "2026-01-01 10:00:00" }]]) {
        const r = await call(route, body);
        check(`${route} needs a token`, r.status === 401, `status ${r.status}`);
    }
    check("a garbage token is refused", (await call("/user", { userID: 1 }, "not-a-token")).status === 401);
    check("a token with a tampered signature is refused", (await call("/user", { userID: 1 }, tA.slice(0, -2) + (tA.endsWith("AA") ? "BB" : "AA"))).status === 401);
    const unsigned = `${b64({ alg: "none", typ: "JWT" })}.${b64({ sub: String(bob.userID) })}.`;
    check("an unsigned (alg: none) token is refused", (await call("/user", { userID: 1 }, unsigned)).status === 401);
    check("a valid token works", (await post("/user", { userID: bob.userID }, tA)).userName === "bob");

    console.log("search");
    const found = await post("/search", { searchTable: "users", searchCols: "userName, userEmail", searchTerm: "bob" }, tA);
    check("finds a user by name", Array.isArray(found.results) && found.results.some(u => u.userID === bob.userID), JSON.stringify(found).slice(0, 120));
    check("SQL in the table name is rejected", /invalid parameter/.test((await post("/search", { searchTable: "users; DROP TABLE messages", searchCols: "userName", searchTerm: "bob" }, tA)).error));
    check("SQL in the column list is rejected", /invalid parameter/.test((await post("/search", { searchTable: "users", searchCols: "userName) OR 1=1 --", searchTerm: "bob" }, tA)).error));
    const evilTerm = await post("/search", { searchTable: "users", searchCols: "userName, userEmail", searchTerm: "' OR '1'='1" }, tA);
    const evilWords = ["'", "OR", "'1'='1"];
    check("SQL in the search term is just text: it does not return every user",
        Array.isArray(evilTerm.results) && evilTerm.results.every(u => evilWords.some(w => (u.userName + u.userEmail).toLowerCase().includes(w.toLowerCase()))), JSON.stringify(evilTerm).slice(0, 120));
    const evilId = await post("/friends", { userID: "0 OR 1=1" }, tA);
    check("SQL in a user id is refused (it is not your id)", evilId.friends === undefined && evilId.error === "forbidden", JSON.stringify(evilId).slice(0, 100));

    console.log("friends");
    check("add a friend", !(await post("/friends/add", { userID1: alice.userID, userID2: bob.userID }, tA)).error);
    check("friend appears in the list", JSON.stringify(await post("/friends", { userID: alice.userID }, tA)).includes(`"userID":${bob.userID}`));
    check("you cannot add a friend on behalf of someone else", (await call("/friends/add", { userID1: bob.userID, userID2: carol.userID }, tA)).status === 403);
    check("you cannot befriend yourself", /yourself/.test((await post("/friends/add", { userID1: alice.userID, userID2: alice.userID }, tA)).error));
    check("you cannot read someone else's friends", (await call("/friends", { userID: bob.userID }, tA)).status === 403);
    check("you cannot remove someone else's friendship", (await call("/friends/remove", { userID1: bob.userID, userID2: alice.userID }, tC)).status === 403);
    check("the friend list defaults to your own", JSON.stringify(await post("/friends", {}, tA)).includes(`"userID":${bob.userID}`));
    check("remove a friend", !(await post("/friends/remove", { userID1: alice.userID, userID2: bob.userID }, tA)).error);

    console.log("messages over HTTP");
    check("send a message", Number.isInteger((await post("/messages/send", { sender: alice.userID, receiver: bob.userID, content: "secret hello", date: "2026-01-01 10:00:00" }, tA)).messageID));
    await post("/messages/send", { sender: alice.userID, receiver: bob.userID, content: "dated 1999", date: "1999-01-01 00:00:00" }, tA);
    const stamped = (await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tA)).messages.find(m => m.content === "dated 1999");
    check("the server stamps the time itself (the client's date is ignored)", stamped !== undefined && new Date(stamped.date).getFullYear() >= 2026, JSON.stringify(stamped));
    check("a message without a date is accepted", Number.isInteger((await post("/messages/send", { sender: alice.userID, receiver: bob.userID, content: "no date" }, tA)).messageID));
    check("you cannot send as someone else",(await call("/messages/send", { sender: bob.userID, receiver: alice.userID, content: "forged", date: "2026-01-01 10:00:00" }, tA)).status === 403);
    check("non-string content is refused", (await post("/messages/send", { sender: alice.userID, receiver: bob.userID, content: { a: 1 }, date: "2026-01-01 10:00:00" }, tA)).error === "invalid parameters");
    check("the sender reads the thread", JSON.stringify(await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tA)).includes("secret hello"));
    check("the receiver reads the thread", JSON.stringify(await post("/messages", { userID1: bob.userID, userID2: alice.userID }, tB)).includes("secret hello"));
    check("a third user cannot read the thread", (await call("/messages", { userID1: alice.userID, userID2: bob.userID }, tC)).status === 403);
    check("nor ask for the chat list of someone else", (await call("/chats", { userID: alice.userID }, tC)).status === 403);
    check("the chat list shows your conversation", JSON.stringify(await post("/chats", { userID: bob.userID }, tB)).includes("secret hello"));
    check("a forged message was not stored", !JSON.stringify(await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tA)).includes("forged"));

    console.log("real-time (Socket.IO)");
    let refused = null;
    await connect(null).catch(e => { refused = e; });
    check("a connection without a token is refused", refused !== null && /authentication/.test(refused.message), String(refused));
    refused = null;
    await connect("not-a-token").catch(e => { refused = e; });
    check("a connection with a bad token is refused", refused !== null);

    const sockA = await connect(tA), sockB = await connect(tB), sockC = await connect(tC);
    sockC.emit("init", bob.userID);                                  // carol pretends to be bob
    const spy = [];
    sockC.on("chat message", m => spy.push(m));
    const received = new Promise(resolve => sockB.on("chat message", resolve));
    const payload = { sender: alice.userID, receiver: bob.userID, content: "hello in real time", date: "2026-01-01 10:01:00" };
    sockA.emit("chat message", JSON.stringify(payload));
    const got = await Promise.race([received, sleep(3000).then(() => null)]);
    check("the receiver gets the message instantly", got !== null && JSON.parse(got).content === "hello in real time");
    await sleep(300);
    check("the message was saved", JSON.stringify(await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tA)).includes("hello in real time"));
    check("claiming another user's id in init does not intercept their messages", spy.length === 0, JSON.stringify(spy));

    sockC.emit("chat message", JSON.stringify({ sender: alice.userID, receiver: bob.userID, content: "socket forgery", date: "2026-01-01 10:02:00" }));
    await sleep(400);
    check("a message sent as someone else over the socket is dropped", !JSON.stringify(await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tA)).includes("socket forgery"));

    console.log("robustness");
    sockA.emit("chat message", "this is {not json");
    sockA.emit("chat message", JSON.stringify({ sender: "abc", receiver: null, content: 42 }));
    sockA.emit("chat message", JSON.stringify({ ...payload, messageID: 1, content: "extra field ignored" }));
    await sleep(500);
    check("the server survives malformed socket messages", (await post("/user", { userID: bob.userID }, tA)).userName === "bob");
    check("only valid messages were saved", !JSON.stringify(await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tA)).includes("not json"));
    check("a malformed password hash cannot crash sign-in", (await post("/signin", { userEmail: emailOf("alice"), userPassword: { a: 1 } })).error === "invalid parameters");

    // ---- attachments ------------------------------------------------------------------------------------------
    console.log("attachments");
    const upload = async (token, name, mime, bytes) => {
        const headers = { "Content-Type": mime };
        if (token) headers["Authorization"] = "Bearer " + token;
        const res = await fetch(`${API}/upload?name=${encodeURIComponent(name)}`, { method: "POST", headers, body: bytes });
        return res.headers.get("content-type").includes("json") ? { status: res.status, body: await res.json() } : { status: res.status, body: {} };
    };
    const download = async (token, name) => {
        const res = await fetch(`${API}/files/${name}`, { headers: token ? { Authorization: "Bearer " + token } : {} });
        return { status: res.status, headers: res.headers, bytes: Buffer.from(await res.arrayBuffer()) };
    };
    const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "base64");
    const events = (sock, name) => { const list = []; sock.on(name, m => list.push(JSON.parse(m))); return list; };

    check("uploading needs a token", (await upload(null, "a.png", "image/png", PNG)).status === 401);
    check("an empty file is refused", (await upload(tA, "a.png", "image/png", Buffer.alloc(0))).body.error !== undefined);
    check("a malformed content type is refused", (await upload(tA, "a.png", "not a type", PNG)).body.error === "invalid content type");
    const tooBig = await upload(tA, "big.bin", "application/octet-stream", Buffer.alloc(16 * 1024 * 1024));
    check("a file over 15 MB is refused", tooBig.status === 413, `status ${tooBig.status}`);

    const up1 = (await upload(tA, "holiday photo.png", "image/png", PNG)).body;
    check("an image uploads", typeof up1.attachment === "string" && /^[a-f0-9]{32}\.png$/.test(up1.attachment), JSON.stringify(up1));
    const receivedB = events(sockB, "chat message"), receivedA = events(sockA, "chat message");
    const img = await post("/messages/send", { receiver: bob.userID, type: "image", attachment: up1.attachment, content: "look" }, tA);
    check("an image message is sent", img.message && img.message.type === "image" && img.message.fileName === "holiday photo.png" && img.message.content === "look", JSON.stringify(img));
    check("the same upload cannot be attached twice", (await post("/messages/send", { receiver: bob.userID, type: "image", attachment: up1.attachment }, tA)).error === "unknown attachment");
    const up2 = (await upload(tA, "mine.png", "image/png", PNG)).body;
    check("you cannot attach someone else's upload", (await post("/messages/send", { receiver: bob.userID, type: "image", attachment: up2.attachment }, tC)).error === "unknown attachment");
    check("an unknown attachment name is refused", (await post("/messages/send", { receiver: bob.userID, type: "image", attachment: "0".repeat(32) }, tA)).error === "unknown attachment");
    check("a non-image cannot be sent as an image", (await post("/messages/send", { receiver: bob.userID, type: "image", attachment: (await upload(tA, "x.txt", "text/plain", Buffer.from("hi"))).body.attachment }, tA)).error !== undefined);
    check("an unknown message type is refused", (await post("/messages/send", { receiver: bob.userID, type: "video" }, tA)).error === "invalid message type");
    check("a message needs a receiver or a group, not both", /exactly one/.test((await post("/messages/send", { receiver: bob.userID, groupID: 1, content: "x" }, tA)).error));
    check("you cannot message yourself", /yourself/.test((await post("/messages/send", { receiver: alice.userID, content: "x" }, tA)).error));
    await sleep(300);
    check("the receiver is notified of the image in real time, with its id", receivedB.some(m => m.messageID === img.messageID && m.type === "image"));
    check("the sender's other screens are notified too", receivedA.some(m => m.messageID === img.messageID));

    const got1 = await download(tB, up1.attachment);
    check("the receiver downloads the image, byte for byte", got1.status === 200 && got1.bytes.equals(PNG) && got1.headers.get("content-type") === "image/png");
    check("the download is hardened (nosniff + sandbox)", got1.headers.get("x-content-type-options") === "nosniff" && /sandbox/.test(got1.headers.get("content-security-policy")));
    check("the sender downloads it too", (await download(tA, up1.attachment)).status === 200);
    check("a third user cannot download it", (await download(tC, up1.attachment)).status === 404);
    check("downloading needs a token", (await download(null, up1.attachment)).status === 401);
    check("a path-like file name is refused", (await download(tA, "..%2Fserver.js")).status === 404);

    const upAudio = (await upload(tA, "voice.m4a", "audio/mp4", Buffer.from("fake audio bytes"))).body;
    const voice = await post("/messages/send", { receiver: bob.userID, type: "audio", attachment: upAudio.attachment, duration: 4200 }, tA);
    check("a voice note is sent with its duration", voice.message && voice.message.type === "audio" && voice.message.duration === 4200, JSON.stringify(voice));
    const upBig = (await upload(tA, "long.m4a", "audio/mp4", Buffer.from("x"))).body;
    check("a voice note longer than 10 minutes gets no duration", (await post("/messages/send", { receiver: bob.userID, type: "audio", attachment: upBig.attachment, duration: 99999999 }, tA)).message.duration === null);

    const upHtml = (await upload(tA, "page.html", "text/html", Buffer.from("<script>alert(1)</script>"))).body;
    const doc = await post("/messages/send", { receiver: bob.userID, type: "file", attachment: upHtml.attachment }, tA);
    check("a document is sent with its name and size", doc.message && doc.message.fileName === "page.html" && doc.message.fileSize === 25, JSON.stringify(doc));
    const gotDoc = await download(tB, upHtml.attachment);
    check("a document is always downloaded, never displayed (even HTML)", /^attachment/.test(gotDoc.headers.get("content-disposition")) && /sandbox/.test(gotDoc.headers.get("content-security-policy")));

    // ---- replies, edits, deletions -----------------------------------------------------------------------------
    console.log("replies, edits and deletions");
    const m1 = (await post("/messages/send", { receiver: bob.userID, content: "original text" }, tA)).message;
    const updatesB = events(sockB, "message updated");
    const reply = (await post("/messages/send", { receiver: alice.userID, content: "a reply", replyTo: m1.messageID }, tB)).message;
    check("a reply carries a preview of the message it answers", reply && reply.replyTo === m1.messageID && reply.reply && reply.reply.content === "original text" && reply.reply.senderName === "alice", JSON.stringify(reply));
    const other = (await post("/messages/send", { receiver: carol.userID, content: "to carol" }, tA)).message;
    check("you cannot reply to a message of another conversation", /not in this conversation/.test((await post("/messages/send", { receiver: alice.userID, content: "x", replyTo: other.messageID }, tB)).error));

    const edited = await post("/messages/edit", { messageID: m1.messageID, content: "edited text" }, tA);
    check("you can edit your own message", edited.message && edited.message.content === "edited text" && edited.message.edited === true, JSON.stringify(edited));
    await sleep(300);
    check("the other side is told about the edit", updatesB.some(u => u.messageID === m1.messageID && u.content === "edited text"));
    check("you cannot edit someone else's message", (await call("/messages/edit", { messageID: m1.messageID, content: "hacked" }, tB)).status === 403);
    check("an image cannot be edited", /only text/.test((await post("/messages/edit", { messageID: img.messageID, content: "x" }, tA)).error));
    check("an empty edit is refused", (await post("/messages/edit", { messageID: m1.messageID, content: "  " }, tA)).error === "invalid content");

    check("you cannot delete someone else's message", (await call("/messages/delete", { messageID: m1.messageID }, tB)).status === 403);
    const deleted = await post("/messages/delete", { messageID: m1.messageID }, tA);
    check("you can delete your own message", deleted.message && deleted.message.deleted === true && deleted.message.content === "", JSON.stringify(deleted));
    const afterDelete = (await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tB)).messages.find(m => m.messageID === m1.messageID);
    check("a deleted message keeps its place but loses its text", afterDelete && afterDelete.deleted && afterDelete.content === "" && !JSON.stringify(afterDelete).includes("edited text"));
    check("a reply to a deleted message shows no text", (await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tB)).messages.find(m => m.messageID === reply.messageID).reply.deleted === true);
    check("a deleted message cannot be edited again", (await post("/messages/edit", { messageID: m1.messageID, content: "zombie" }, tA)).error === "the message was deleted");
    const delImg = await post("/messages/delete", { messageID: img.messageID }, tA);
    check("deleting an image hides the attachment", delImg.message && delImg.message.attachment === null);

    // ---- unread counts and read receipts ------------------------------------------------------------------------
    console.log("read receipts");
    const readEvents = events(sockA, "messages read");
    const convBefore = (await post("/conversations", {}, tB)).conversations.find(c => c.kind === "user" && c.id === alice.userID);
    check("the conversation list counts unread messages", convBefore && convBefore.unread >= 3, JSON.stringify(convBefore));
    check("the list shows the last message and the person", convBefore.name === "alice" && convBefore.preview && convBefore.preview.type !== undefined);
    const sent = (await post("/messages/send", { receiver: bob.userID, content: "are you there?" }, tA)).message;
    check("a new message starts unread", sent.readAt === null);
    const readRes = await post("/messages/read", { userID: alice.userID }, tB);
    check("marking a conversation read reports how many messages", readRes.read >= 4, JSON.stringify(readRes));
    await sleep(300);
    check("the sender is told which messages were read", readEvents.some(e => e.by === bob.userID && e.ids.includes(sent.messageID)), JSON.stringify(readEvents));
    check("the unread count drops to zero", (await post("/conversations", {}, tB)).conversations.find(c => c.id === alice.userID && c.kind === "user").unread === 0);
    check("the history shows when it was read", (await post("/messages", { userID1: alice.userID, userID2: bob.userID }, tA)).messages.find(m => m.messageID === sent.messageID).readAt !== null);
    check("only the other person's messages count as unread (your own never do)", (await post("/conversations", {}, tA)).conversations.find(c => c.id === bob.userID && c.kind === "user").unread === 1);

    // ---- history pages ------------------------------------------------------------------------------------------
    console.log("pagination");
    for (let i = 1; i <= 35; i++) await post("/messages/send", { receiver: carol.userID, content: `page test ${i}` }, tA);
    const page1 = await post("/messages", { userID1: alice.userID, userID2: carol.userID }, tA);
    check("the latest 30 messages come first, oldest of them first", page1.messages.length === 30 && page1.hasMore === true && page1.messages[29].content === "page test 35" && page1.messages[0].content === "page test 6", `${page1.messages.length} ${page1.hasMore}`);
    const page2 = await post("/messages", { userID1: alice.userID, userID2: carol.userID, before: page1.messages[0].messageID }, tA);
    check("the page before continues without a gap", page2.messages.length === 6 && page2.messages[page2.messages.length - 1].content === "page test 5" && page2.hasMore === false, `${page2.messages.length}`);
    check("the page size can be chosen, and is capped", (await post("/messages", { userID1: alice.userID, userID2: carol.userID, limit: 5 }, tA)).messages.length === 5
        && (await post("/messages", { userID1: alice.userID, userID2: carol.userID, limit: 100000 }, tA)).messages.length <= 100);
    check("a non-numeric cursor is refused", (await post("/messages", { userID1: alice.userID, userID2: carol.userID, before: "1 OR 1=1" }, tA)).error === "invalid parameters");

    // ---- typing and presence ------------------------------------------------------------------------------------
    console.log("typing and presence");
    const typingB = events(sockB, "typing"), typingC = events(sockC, "typing");
    sockA.emit("typing", JSON.stringify({ to: bob.userID, typing: true }));
    sockA.emit("typing", "not json");
    await sleep(300);
    check("the other person sees that you are typing", typingB.length === 1 && typingB[0].from === alice.userID && typingB[0].typing === true, JSON.stringify(typingB));
    check("nobody else does", typingC.length === 0);

    const presenceA = events(sockA, "presence");
    sockC.close();
    await sleep(500);
    check("your contacts are told when you go offline, with the time", presenceA.some(p => p.userID === carol.userID && p.online === false && typeof p.lastSeen === "string"), JSON.stringify(presenceA));
    check("the profile shows you are offline", (await post("/user", { userID: carol.userID }, tA)).online === false);
    const sockC2 = await connect(tC);
    await sleep(500);
    check("and when you come back", presenceA.some(p => p.userID === carol.userID && p.online === true));
    check("the profile shows you are online", (await post("/user", { userID: carol.userID }, tA)).online === true);
    check("the last seen time is saved", typeof (await post("/user", { userID: carol.userID }, tA)).lastSeen === "string");

    // ---- groups -------------------------------------------------------------------------------------------------
    console.log("groups");
    const dave = await post("/signup", { userName: "dave", userEmail: emailOf("dave"), userPassword: "pw-dave" });
    const sockD = await connect(dave.token);
    check("a group needs a name", /name/.test((await post("/groups/create", { name: "  ", members: [bob.userID] }, tA)).error));
    check("a group needs another member", /at least one/.test((await post("/groups/create", { name: "solo", members: [alice.userID] }, tA)).error));
    check("an unknown member is refused", (await post("/groups/create", { name: "ghosts", members: [bob.userID, 99999999] }, tA)).error === "unknown member");
    check("members must be a list", (await post("/groups/create", { name: "bad", members: "1,2" }, tA)).error === "invalid parameters");
    const groupEventsB = events(sockB, "group updated");
    const created = await post("/groups/create", { name: "Weekend trip", members: [bob.userID, carol.userID] }, tA);
    check("a group is created", Number.isInteger(created.groupID), JSON.stringify(created));
    const gid = created.groupID;
    await sleep(300);
    check("the members are told", groupEventsB.some(e => e.groupID === gid));
    const info = (await post("/groups/info", { groupID: gid }, tB)).group;
    check("a member sees the group and its members", info && info.name === "Weekend trip" && info.members.length === 3 && info.me === "member"
        && info.members.find(m => m.userID === alice.userID).role === "admin" && info.members.find(m => m.userID === alice.userID).online === true, JSON.stringify(info));
    check("an outsider cannot see the group", (await call("/groups/info", { groupID: gid }, dave.token)).status === 403);

    const gA = events(sockA, "chat message"), gC = events(sockC2, "chat message"), gD = events(sockD, "chat message");
    const gmsg = (await post("/messages/send", { groupID: gid, content: "who is driving?" }, tB)).message;
    check("a member posts to the group", gmsg && gmsg.groupID === gid && gmsg.receiver === null && gmsg.senderName === "bob", JSON.stringify(gmsg));
    await sleep(300);
    check("every member receives it in real time", gA.some(m => m.messageID === gmsg.messageID) && gC.some(m => m.messageID === gmsg.messageID));
    check("an outsider receives nothing", gD.length === 0);
    check("an outsider cannot post", /not a member/.test((await post("/messages/send", { groupID: gid, content: "hi" }, dave.token)).error));
    check("an outsider cannot read the history", (await call("/groups/messages", { groupID: gid }, dave.token)).status === 403);
    check("a member reads the history with sender names", (await post("/groups/messages", { groupID: gid }, tC)).messages.some(m => m.content === "who is driving?" && m.senderName === "bob"));
    const groupImg = (await upload(tB, "map.png", "image/png", PNG)).body;
    await post("/messages/send", { groupID: gid, type: "image", attachment: groupImg.attachment }, tB);
    check("group members can download an image sent to the group", (await download(tC, groupImg.attachment)).status === 200 && (await download(dave.token, groupImg.attachment)).status === 404);
    check("a reply works inside a group", (await post("/messages/send", { groupID: gid, content: "me", replyTo: gmsg.messageID }, tC)).message.reply.content === "who is driving?");

    const convC = (await post("/conversations", {}, tC)).conversations.find(c => c.kind === "group" && c.id === gid);
    check("the group is in the conversation list with unread messages", convC && convC.name === "Weekend trip" && convC.unread === 2 && convC.members === 3, JSON.stringify(convC));
    await post("/messages/read", { groupID: gid }, tC);
    check("reading the group clears its unread count", (await post("/conversations", {}, tC)).conversations.find(c => c.kind === "group" && c.id === gid).unread === 0);
    check("you do not count your own messages as unread", (await post("/conversations", {}, tB)).conversations.find(c => c.kind === "group" && c.id === gid).unread === 1);

    check("a plain member cannot add people", (await call("/groups/add", { groupID: gid, userID: dave.userID }, tB)).status === 403);
    check("a plain member cannot rename", (await call("/groups/rename", { groupID: gid, name: "mine now" }, tB)).status === 403);
    check("an admin renames the group", (await post("/groups/rename", { groupID: gid, name: "Summer trip" }, tA)).name === "Summer trip");
    const groupEventsD = events(sockD, "group updated");
    check("an admin adds a member", (await post("/groups/add", { groupID: gid, userID: dave.userID }, tA)).added === dave.userID);
    check("adding twice is refused", (await post("/groups/add", { groupID: gid, userID: dave.userID }, tA)).error === "already a member");
    await sleep(300);
    check("the new member is told", groupEventsD.some(e => e.groupID === gid));
    check("the new member has nothing unread from before they joined", (await post("/conversations", {}, dave.token)).conversations.find(c => c.id === gid && c.kind === "group").unread === 0);
    check("a plain member cannot remove someone else", (await call("/groups/remove", { groupID: gid, userID: carol.userID }, tB)).status === 403);
    const removedEvents = events(sockC2, "group updated");
    check("an admin removes a member", (await post("/groups/remove", { groupID: gid, userID: carol.userID }, tA)).removed === carol.userID);
    await sleep(300);
    check("the removed member is told", removedEvents.some(e => e.groupID === gid && e.removed === true));
    check("and can no longer read the group", (await call("/groups/messages", { groupID: gid }, tC)).status === 403);
    check("the group is gone from their list", !(await post("/conversations", {}, tC)).conversations.some(c => c.kind === "group" && c.id === gid));
    check("the sole admin leaving promotes another member", (await post("/groups/remove", { groupID: gid, userID: alice.userID }, tA)).removed === alice.userID
        && (await post("/groups/info", { groupID: gid }, tB)).group.me === "admin");
    await post("/groups/remove", { groupID: gid, userID: bob.userID }, tB);
    await post("/groups/remove", { groupID: gid, userID: dave.userID }, dave.token);
    check("an empty group is deleted with its messages", (await call("/groups/info", { groupID: gid }, tB)).status === 403);

    // ---- profile pictures ---------------------------------------------------------------------------------------
    console.log("profile pictures");
    const pic = (await upload(tA, "me.png", "image/png", PNG)).body;
    check("a profile picture is set", (await post("/me/avatar", { attachment: pic.attachment }, tA)).avatar === pic.attachment);
    check("the profile shows it", (await post("/user", { userID: alice.userID }, tB)).avatar === pic.attachment);
    check("search and friends lists carry it", (await post("/search", { searchTable: "users", searchCols: "userName", searchTerm: "alice" }, tB)).results.some(u => u.avatar === pic.attachment));
    check("anyone signed in can see a profile picture", (await download(dave.token, pic.attachment)).status === 200);
    check("nobody can set a picture they did not upload", (await post("/me/avatar", { attachment: (await upload(tA, "p2.png", "image/png", PNG)).body.attachment }, tB)).error === "unknown attachment");
    check("a profile picture must be an image", /image/.test((await post("/me/avatar", { attachment: (await upload(tA, "p3.txt", "text/plain", Buffer.from("x"))).body.attachment }, tA)).error));
    check("the picture can be removed", (await post("/me/avatar", { attachment: null }, tA)).avatar === null && (await post("/user", { userID: alice.userID }, tB)).avatar === null);

    sockC2.close(); sockD.close();

    console.log("brute force protection");
    const victim = await post("/signup", { userName: "victim", userEmail: emailOf("victim"), userPassword: "pw-victim" });
    let last = null;
    for (let i = 0; i < 11; i++) last = await call("/signin", { userEmail: emailOf("victim"), userPassword: "guess" + i });
    check("after 10 failed attempts sign-in is blocked", last.status === 429, `status ${last.status}`);
    check("even the right password is blocked while locked", (await call("/signin", { userEmail: emailOf("victim"), userPassword: "pw-victim" })).status === 429);
    check("other accounts are not affected", (await post("/signin", { userEmail: emailOf("bob"), userPassword: "pw-bob" })).userID === bob.userID);

    sockA.close(); sockB.close(); sockC.close();
    console.log(failures === 0 ? "\nall checks passed" : `\n${failures} check(s) failed`);
    process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.error("test crashed:", e); process.exit(1); });
