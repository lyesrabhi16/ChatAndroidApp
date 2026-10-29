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
    check("SQL in the search term finds nothing", Array.isArray(evilTerm.results) && evilTerm.results.length === 0);
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
    check("you cannot send as someone else", (await call("/messages/send", { sender: bob.userID, receiver: alice.userID, content: "forged", date: "2026-01-01 10:00:00" }, tA)).status === 403);
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
