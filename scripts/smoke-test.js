// End-to-end smoke test of the chat server (HTTP API + Socket.IO).
//   docker compose up --build        (in another terminal)
//   npm run smoke
// Environment: API_URL (default http://localhost:8001), SOCKET_URL (default http://localhost:8000)
const { io } = require("socket.io-client");

const API = process.env.API_URL || "http://localhost:8001";
const SOCKET = process.env.SOCKET_URL || "http://localhost:8000";
let failures = 0;

const post = async (path, body) => (await fetch(API + path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
})).json();

function check(name, condition, detail = "") {
    if (condition) console.log(`  ok    ${name}`);
    else { failures++; console.log(`  FAIL  ${name} ${detail}`); }
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

function connect(userID) {
    return new Promise((resolve, reject) => {
        const socket = io(SOCKET, { transports: ["websocket"], reconnection: false });
        socket.on("init request", () => { socket.emit("init", userID); resolve(socket); });
        socket.on("connect_error", reject);
    });
}

(async () => {
    const suffix = Date.now();
    console.log("accounts");
    const alice = await post("/signup", { userName: "alice", userEmail: `alice${suffix}@example.com`, userPassword: "pw-alice" });
    const bob = await post("/signup", { userName: "bob", userEmail: `bob${suffix}@example.com`, userPassword: "pw-bob" });
    check("sign up returns a userID", Number.isInteger(alice.userID) && Number.isInteger(bob.userID), JSON.stringify(alice));
    check("duplicate e-mail is refused", (await post("/signup", { userName: "alice", userEmail: `alice${suffix}@example.com`, userPassword: "x" })).error === "email already exists");
    const signin = await post("/signin", { userEmail: `alice${suffix}@example.com`, userPassword: "pw-alice" });
    check("sign in with the right password", signin.userID === alice.userID && signin.userName === "alice");
    check("sign in with a wrong password is refused", (await post("/signin", { userEmail: `alice${suffix}@example.com`, userPassword: "nope" })).error === "wrong password");
    check("the password is never returned", !JSON.stringify(signin).includes("pw-alice") && signin.userPassword === undefined);
    check("get a user", (await post("/user", { userID: bob.userID })).userName === "bob");
    check("missing parameter is reported", /missing parameter/.test((await post("/signin", { userEmail: "x" })).error));

    console.log("search");
    const found = await post("/search", { searchTable: "users", searchCols: "userName, userEmail", searchTerm: "bob" });
    check("finds a user by name", Array.isArray(found.results) && found.results.some(u => u.userID === bob.userID), JSON.stringify(found).slice(0, 120));
    const evilTable = await post("/search", { searchTable: "users; DROP TABLE messages", searchCols: "userName", searchTerm: "bob" });
    check("SQL in the table name is rejected", /invalid parameter/.test(evilTable.error), JSON.stringify(evilTable));
    const evilCols = await post("/search", { searchTable: "users", searchCols: "userName) OR 1=1 --", searchTerm: "bob" });
    check("SQL in the column list is rejected", /invalid parameter/.test(evilCols.error), JSON.stringify(evilCols));
    const evilTerm = await post("/search", { searchTable: "users", searchCols: "userName, userEmail", searchTerm: "' OR '1'='1" });
    check("SQL in the search term finds nothing", Array.isArray(evilTerm.results) && evilTerm.results.length === 0, JSON.stringify(evilTerm).slice(0, 120));
    check("the users table is intact after the attacks", (await post("/user", { userID: alice.userID })).userName === "alice");

    console.log("friends");
    check("add a friend", !(await post("/friends/add", { userID1: alice.userID, userID2: bob.userID })).error);
    const friends = await post("/friends", { userID: alice.userID });
    check("friend appears in the list", JSON.stringify(friends).includes(`"userID":${bob.userID}`), JSON.stringify(friends).slice(0, 120));
    check("remove a friend", !(await post("/friends/remove", { userID1: alice.userID, userID2: bob.userID })).error);

    console.log("messages over HTTP");
    check("send a message", Number.isInteger((await post("/messages/send", { sender: alice.userID, receiver: bob.userID, content: "hello over http", date: "2026-01-01 10:00:00" })).messageID));
    const thread = await post("/messages", { userID1: alice.userID, userID2: bob.userID });
    check("the thread contains it", JSON.stringify(thread).includes("hello over http"), JSON.stringify(thread).slice(0, 150));
    const chats = await post("/chats", { userID: bob.userID });
    check("the chat list shows the conversation", JSON.stringify(chats).includes("hello over http"));

    console.log("real-time (Socket.IO)");
    const sockA = await connect(alice.userID), sockB = await connect(bob.userID);
    const received = new Promise(resolve => sockB.on("chat message", resolve));
    const payload = { sender: alice.userID, receiver: bob.userID, content: "hello in real time", date: "2026-01-01 10:01:00" };
    sockA.emit("chat message", JSON.stringify(payload));
    const got = await Promise.race([received, sleep(3000).then(() => null)]);
    check("the receiver gets the message instantly", got !== null && JSON.parse(got).content === "hello in real time");
    await sleep(300);
    check("the message was saved", JSON.stringify(await post("/messages", { userID1: alice.userID, userID2: bob.userID })).includes("hello in real time"));

    console.log("robustness");
    sockA.emit("chat message", "this is {not json");
    sockA.emit("chat message", JSON.stringify({ sender: "abc", receiver: null, content: 42 }));
    sockA.emit("chat message", JSON.stringify({ ...payload, messageID: 1, content: "extra field ignored" }));
    await sleep(500);
    check("the server survives malformed socket messages", (await post("/user", { userID: bob.userID })).userName === "bob");
    check("only valid messages were saved", !JSON.stringify(await post("/messages", { userID1: alice.userID, userID2: bob.userID })).includes("not json"));

    sockA.close(); sockB.close();
    console.log(failures === 0 ? "\nall checks passed" : `\n${failures} check(s) failed`);
    process.exit(failures === 0 ? 0 : 1);
})().catch(e => { console.error("test crashed:", e); process.exit(1); });
