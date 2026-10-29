



const
    argon2 = require("argon2"),
    express = require('express'),
    app = express(),
    // Configuration comes from environment variables; the defaults match a local XAMPP/MySQL setup.
    port = Number(process.env.PORT || 8001),
    socketPort = Number(process.env.SOCKET_PORT || 8000),
    // A pool reconnects by itself when MySQL restarts (a single connection would stay dead).
    dbc = require('mysql2').createPool({
        host: process.env.DB_HOST || "localhost",
        port: Number(process.env.DB_PORT || 3306),
        user: process.env.DB_USER || "root",
        password: process.env.DB_PASSWORD || "",
        database: process.env.DB_NAME || "ChatDB",
        connectionLimit: 10
    });

dbc.query("SELECT 1", err => {
        if (err) console.info("failed to connect to DB. ",err.code || err);
        else{
            console.info("DB connected.");
        }
});

// /upload receives a raw file body, so the JSON and form parsers must leave it alone
const jsonBody = express.json(), formBody = express.urlencoded({ extended: false });
app.use((req, res, next) => req.path === "/upload" ? next() : jsonBody(req, res, next));
app.use((req, res, next) => req.path === "/upload" ? next() : formBody(req, res, next));

// ---- authentication -------------------------------------------------------------------------------------------
// /signup and /signin return a signed token (JWT). Every other route and every Socket.IO connection must present it,
// and the identity used for authorization always comes from the token, never from an id sent by the client.
const jwt = require("jsonwebtoken"), crypto = require("crypto");
const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(32).toString("hex");
if (!process.env.JWT_SECRET) {
    console.warn("JWT_SECRET is not set: using a random secret, so every token stops working when the server restarts.");
}
const TOKEN_LIFETIME = process.env.TOKEN_LIFETIME || "30d";

const issueToken = userID => jwt.sign({ sub: String(userID) }, JWT_SECRET, { algorithm: "HS256", expiresIn: TOKEN_LIFETIME });

function userIDFromToken(token) {
    try {
        const id = Number(jwt.verify(token, JWT_SECRET, { algorithms: ["HS256"] }).sub);
        return Number.isInteger(id) ? id : null;
    } catch (e) {
        return null;
    }
}

function requireAuth(req, res, next) {
    const header = req.headers["authorization"] || "";
    const id = header.startsWith("Bearer ") ? userIDFromToken(header.slice(7)) : null;
    if (id === null) {
        res.status(401).json({ error: "authentication required" });
        return;
    }
    req.userID = id;
    next();
}

const forbidden = res => res.status(403).json({ error: "forbidden" });

// argon2.verify throws on a malformed stored hash; that must be a failed sign-in, not an unhandled rejection
async function verifyPassword(hash, password) {
    try {
        return await argon2.verify(hash, password);
    } catch (e) {
        return false;
    }
}

// The caller may only act as themselves: a missing id becomes their own, a different id is refused.
const asSelf = field => (req, res, next) => {
    if (req.body[field] === undefined) req.body[field] = req.userID;
    else if (Number(req.body[field]) !== req.userID) { forbidden(res); return; }
    next();
};
// Field types: a JSON client could otherwise send objects or arrays where a string or number is expected.
const mustBeStrings = (...fields) => (req, res, next) => {
    if (fields.some(f => typeof req.body[f] !== "string" || req.body[f].length === 0)) { res.json({ error : "invalid parameters" }); return; }
    next();
};
const mustBeIntegers = (...fields) => (req, res, next) => {
    if (fields.some(f => !Number.isInteger(Number(req.body[f])))) { res.json({ error : "invalid parameters" }); return; }
    next();
};
const notYourself = field => (req, res, next) => {
    if (Number(req.body[field]) === req.userID) { res.json({ error : "you cannot do this with yourself" }); return; }
    next();
};

// A conversation can only be read by one of its two participants.
const participant = (a, b) => (req, res, next) => {
    if (Number(req.body[a]) !== req.userID && Number(req.body[b]) !== req.userID) { forbidden(res); return; }
    next();
};

// At most 10 failed sign-ins per client address and e-mail every 15 minutes (slows down password guessing).
const failedSignins = new Map();
const signinKey = req => req.ip + "|" + String(req.body["userEmail"]).toLowerCase();
function tooManyFailures(key) {
    const entry = failedSignins.get(key);
    if (entry && entry.resetAt < Date.now()) failedSignins.delete(key);
    return entry !== undefined && entry.resetAt >= Date.now() && entry.count >= 10;
}
function recordFailure(key) {
    const entry = failedSignins.get(key);
    if (entry && entry.resetAt >= Date.now()) entry.count++;
    else failedSignins.set(key, { count: 1, resetAt: Date.now() + 15 * 60 * 1000 });
}

app.post("/signup", async (req, res) => {
    
    if(req.body["userName"] === undefined){
        let message = "missing parameter : userName"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["userEmail"] === undefined){
        let message = "missing parameter : userEmail"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["userPassword"] === undefined){
        let message = "missing parameter : userPassword"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    
    const name = req.body["userName"], email = req.body["userEmail"], password = req.body["userPassword"];
    if (typeof name !== "string" || typeof email !== "string" || typeof password !== "string") {
        res.json({error : "invalid parameters"});
        return;
    }
    // limits follow the columns of the users table (userName varchar(20), userEmail varchar(50))
    if (name.trim().length === 0 || name.length > 20) {
        res.json({error : "userName must be 1 to 20 characters"});
        return;
    }
    if (email.trim().length === 0 || email.length > 50) {
        res.json({error : "userEmail must be 1 to 50 characters"});
        return;
    }
    if (password.length < 6 || password.length > 200) {
        res.json({error : "userPassword must be 6 to 200 characters"});
        return;
    }

    let
        response = undefined,
        user = {
            userName : req.body["userName"],
            userEmail : req.body["userEmail"],
            userPassword : await argon2.hash(password) 
        },
        sql = "INSERT INTO `users` SET ?";
    dbc.query(
        sql,
        user,
        (err, result)=>{
            if (err){
                switch (err.code) {
                    case "ER_DUP_ENTRY":
                        response = {error : "email already exists"};
                        console.error("email already exists");
                        res.json(response);
                        break;
                    default:
                        response = {error : "unknown error : "+ err.code};
                        res.json(response);
                        break;
                }
            }
            else {
                console.info('user registered');
                response = {
                    userID    : result.insertId,
                    token     : issueToken(result.insertId)
                };
                res.json(response);
            }
        }
    )
});

app.post("/signin", (req, res) => {

    if(req.body["userEmail"] === undefined){
        let message = "missing parameter : userEmail"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["userPassword"] === undefined){
        let message = "missing parameter : userPassword"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    

    if (typeof req.body["userEmail"] !== "string" || typeof req.body["userPassword"] !== "string") {
        res.json({error : "invalid parameters"});
        return;
    }
    const attemptKey = signinKey(req);
    if (tooManyFailures(attemptKey)) {
        res.status(429).json({error : "too many failed attempts, try again later"});
        return;
    }

    let
        response = undefined,
        user = {
            userEmail : req.body["userEmail"],
            userPassword : req.body["userPassword"]
        },
        sql = "SELECT * FROM users WHERE userEmail = ?";
    
    dbc.query(sql, [user.userEmail], async (err, result) => {
        if (err){
                response = {error : ""+ err.code};
                res.json(response);
            }
        
        else {
            let result0 = result[0];
            if ( result0 === undefined ){
                // same message as a wrong password, so the response does not reveal which e-mails are registered
                recordFailure(attemptKey);
                console.error("sign in failed: unknown e-mail");
                response = {error : "invalid email or password"};
                res.json(response);
            }
            else{
                if (await verifyPassword(result0.userPassword, user.userPassword)){
                    console.info('user signed in');
                    failedSignins.delete(attemptKey);
                    response = {
                        userID    : result0.userID,
                        userEmail : result0.userEmail,
                        userName : result0.userName,
                        avatar : result0.avatar,
                        token : issueToken(result0.userID)
                    };
                    res.json(response);
                }
                else{
                    recordFailure(attemptKey);
                    console.error("sign in failed: wrong password");
                    response = {error : "invalid email or password"};
                    res.json(response);
                }
            }
            
        }
        
    })
});

app.post("/user", requireAuth, (req, res) =>{

    if(req.body["userID"] === undefined){
        let message = "missing parameter : userID"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }

    let 
        response = undefined,
        user = {
            userID : req.body["userID"]
        },
        sql = "SELECT * FROM users WHERE userID = ?";
    
    dbc.query(sql, [user.userID], (err, result) => {
        if (err){
                response = {error : ""+ err.code};
                res.json(response);
            }
        
        else {
            let result0 = result[0];
            if ( result0 === undefined ){
                console.error("id does not exits");
                response = {error : "id does not exits"};
                res.json(response);
            }
            else{                
                response = {
                    userID : result0.userID,
                    userName : result0.userName,
                    userEmail : result0.userEmail,
                    avatar : result0.avatar,
                    online : isOnline(result0.userID),
                    lastSeen : result0.lastSeen ? new Date(result0.lastSeen).toISOString() : null,
                };
                console.error("user : " ,response);
                res.json(response);
            }
            
        }
        
    })
});

app.post("/search", requireAuth, (req, res) => {
    if(req.body["searchTerm"] === undefined){
        let message = "missing parameter : searchTerm"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["searchTable"] === undefined){
        let message = "missing parameter : searchTable"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["searchCols"] === undefined){
        let message = "missing parameter : searchCols"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }

    // The table and columns are chosen by the client, so they are checked against a whitelist; every value
    // goes through a bound parameter (they used to be pasted into the SQL text: an SQL injection).
    const SEARCH_TABLE = "users", SEARCH_COLUMNS = ["userName", "userEmail"];
    let
        response = undefined,
        table = String(req.body["searchTable"]),
        colList = String(req.body["searchCols"]).split(",").map(c => c.trim()),
        term = String(req.body["searchTerm"]);

    if (table !== SEARCH_TABLE || colList.length === 0 || !colList.every(c => SEARCH_COLUMNS.includes(c))) {
        let message = "invalid parameter : searchTable or searchCols";
        console.error(message);
        res.json({error : message});
        return;
    }

    // full-text operators in the user's words (+ - < > ( ) ~ * " @) are dropped, they are not part of a name
    let termWords = term.split(" ").map(w => w.replace(/[+\-<>()~*"@]/g, "")).filter(w => w.length > 0);
    if (termWords.length === 0) {
        res.json({results : []});
        return;
    }

    let
        cols = colList.join(","),  // identifiers from the whitelist above
        likePattern = w => "%" + w.replace(/[\\%_]/g, "\\$&") + "%",
        conditions = termWords.map(() => colList.map(c => "CONVERT(" + c + " USING utf8) LIKE ?").join(" OR ")).join(" OR "),
        params = [
            '"' + termWords.join(" ") + '"',                    // exact phrase
            termWords.map(w => "+" + w).join(" "),              // all the words
            termWords.join(" "),                                // some of the words
            termWords.map(w => w + "*").join(" "),              // word prefixes
            ...termWords.flatMap(w => colList.map(() => likePattern(w)))
        ],
        sql = `SELECT t.userID, t.userName, t.userEmail, t.avatar FROM
            (SELECT *,
             MATCH(${cols}) AGAINST(? IN BOOLEAN MODE) score_exact,
             MATCH(${cols}) AGAINST(? IN BOOLEAN MODE) score_all,
             MATCH(${cols}) AGAINST(? IN BOOLEAN MODE) score_some,
             MATCH(${cols}) AGAINST(? IN BOOLEAN MODE) score_pre
             FROM (
                SELECT userID, userName, userEmail, avatar FROM users WHERE (${conditions})
             ) as f
            ) as t
            ORDER BY t.score_exact DESC, t.score_all DESC, t.score_some DESC, t.score_pre DESC
            LIMIT 100`.replace(/\n/g, " ");

    dbc.query(sql, params, (err, results)=>{
        if (err){
            response = {error : ""+ err.code};
            res.json(response);
        }
        else{
            response = {
                "results" : results
            }
            res.json(response);
        }
    })
    

});

app.post("/chats", requireAuth, asSelf("userID"), (req, res)=>{
    if(req.body["userID"] === undefined){
        let message = "missing parameter : userID"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }

    let 
        response = undefined,
        userID = req.body["userID"],
        sql = `
                SELECT 
                    users.userID, users.userName, users.userEmail,
                    r.messageID, r.sender, r.receiver, r.date, r.content
                FROM users,
                    (
                        SELECT *
                        FROM messages
                        WHERE
                            sender = ?
                            OR
                            receiver = ?
                    ) as r
                WHERE userID = r.sender OR userID = r.receiver
                ORDER BY r.date DESC, r.messageID DESC
            `.replace(/\n/g, " ");

    dbc.query(sql, [userID, userID], (err, results)=>{
        if (err){
            response = {error : ""+ err.code};
            res.json(response);
        }
        else {               
            response = {
                messages : results 
            };
            res.json(response);
        
        }
    })

});

app.post("/friends", requireAuth, asSelf("userID"), (req, res)=>{
    if(req.body["userID"] === undefined){
        let message = "missing parameter : userID"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }

    let 
        response = undefined,
        userID = req.body["userID"],
        sql = `SELECT users.userID, users.userName, users.userEmail, users.avatar
                FROM users,
                    (
                        select * from friends WHERE user1 = ? OR user2 = ?
                    )  as f
                where (userID = f.user1 OR userID = f.user2) AND userID != ?
            `.replace(/\n/g," ");

    dbc.query(
        sql,
        [userID, userID, userID],
        (err, results)=>{
        if (err){
            response = {error : ""+ err.code};
            res.json(response);
        }
        else {               
            response = {
                friends : results 
            };
            res.json(response);
        
        }
    })

});


app.post("/friends/add", requireAuth, asSelf("userID1"), mustBeIntegers("userID2"), notYourself("userID2"), (req, res)=>{
    
    if(req.body["userID1"] === undefined){
        let message = "missing parameter : userID1"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["userID2"] === undefined){
        let message = "missing parameter : userID2"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }

    let 
        response = undefined,
        friends = {
          
            user1:req.body["userID1"],
            user2:req.body["userID2"],
        },
        sql = 'INSERT INTO friends SET ?';
    
    dbc.query(
        sql,
        friends,
        (err, results)=>{
        if (err){
            response = {error : ""+ err.code};
            res.json(response);
        }
        else {               
            response = {
                messages : results 
            };
            res.json(response);
        
        }
    })

});

app.post("/friends/remove", requireAuth, asSelf("userID1"), (req, res)=>{
    if(req.body["userID1"] === undefined){
        let message = "missing parameter : userID1"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["userID2"] === undefined){
        let message = "missing parameter : userID2"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }

    let 
        response = undefined,
        user1 = req.body["userID1"],
        user2 = req.body["userID2"],
        sql = 'DELETE FROM friends WHERE (user1 = ? AND user2 = ?) OR (user1 = ? AND user2 = ?) ';
    
    dbc.query(
        sql,
        [user1,user2,user2,user1],
        (err, results)=>{
        if (err){
            response = {error : ""+ err.code};
            res.json(response);
        }
        else {               
            response = {
                messages : results 
            };
            res.json(response);
        
        }
    })

});

// ---- real time (Socket.IO) --------------------------------------------------------------------------------------
const {Server} = require("socket.io");

// One entry per open connection: a user can be connected from several devices.
const clients = new Set();
const isOnline = userID => [...clients].some(c => c.userID === userID);
function emitToUsers(userIDs, event, payload) {
    const wanted = new Set(userIDs);
    clients.forEach(client => { if (wanted.has(client.userID)) client.socket.emit(event, payload); });
}
const mustBeIntegersMw = mustBeIntegers;
const shared = { app, dbc, requireAuth, emitToUsers, isOnline, mustBeIntegers: mustBeIntegersMw };

require("./server/uploads")(shared);
const messaging = require("./server/messaging")(shared);
require("./server/groups")(shared);

// The people who care that a user is online: friends, people they talk to and members of their groups.
async function contactsOf(userID) {
    const [rows] = await dbc.promise().query(`
        SELECT DISTINCT x AS id FROM (
            SELECT IF(user1 = ?, user2, user1) AS x FROM friends WHERE user1 = ? OR user2 = ?
            UNION SELECT IF(sender = ?, receiver, sender) FROM messages WHERE groupID IS NULL AND (sender = ? OR receiver = ?)
            UNION SELECT other.userID FROM group_members mine JOIN group_members other ON other.groupID = mine.groupID
                  WHERE mine.userID = ? AND other.userID <> ?
        ) t WHERE x IS NOT NULL`, Array(8).fill(userID));
    return rows.map(r => r.id);
}

// A connection must present a valid token, either as `auth: {token}` (Socket.IO 3+ clients) or `?token=...`.
function startSocketServer() {
    const server = new Server(socketPort);
    server.use((socket, next) => {
        const token = (socket.handshake.auth && socket.handshake.auth.token) || socket.handshake.query.token;
        const id = typeof token === "string" ? userIDFromToken(token) : null;
        if (id === null) {
            next(new Error("authentication required"));
            return;
        }
        socket.userID = id;
        next();
    });

    server.on("connection", (socket) => {
        console.info(`Connection detected : [${socket.id}]`)
        socket.emit("message","hello from server");
        socket.emit("init request");

        // The user is whoever the token says, so the socket is registered as soon as it connects: a client does not
        // have to announce itself (and cannot choose its identity). The "init" event of older clients is accepted and ignored.
        const wasOnline = isOnline(socket.userID);
        const client = { socket : socket, userID : socket.userID };
        clients.add(client);
        console.info("Client connected : " + `[${client.userID}, ${client.socket.id}]`);
        console.info(`Connected clients : ${clients.size}`);
        socket.on("init", ()=>{});

        if (!wasOnline) {
            contactsOf(socket.userID).then(ids => emitToUsers(ids, "presence", JSON.stringify({ userID: socket.userID, online: true })))
                .catch(e => console.error(e));
        }

        socket.on("disconnect", () => {
            console.info(`Client disconnected [id=${socket.id}]`);
            clients.delete(client);
            if (!isOnline(socket.userID)) {
                const lastSeen = new Date();
                dbc.query("UPDATE users SET lastSeen = ? WHERE userID = ?", [lastSeen, socket.userID], err => { if (err) console.error(err.code); });
                contactsOf(socket.userID)
                    .then(ids => emitToUsers(ids, "presence", JSON.stringify({ userID: socket.userID, online: false, lastSeen: lastSeen.toISOString() })))
                    .catch(e => console.error(e));
            }
        });

        socket.on("message", (msg) => {
            console.info("Client ["+ socket.id +"] sends : ["+ msg +"]");
        })

        // { to: <userID> } or { groupID }, typing: true | false. Relayed to the other side, never stored.
        socket.on("typing", async (raw) => {
            try {
                const data = typeof raw === "string" ? JSON.parse(raw) : raw;
                if (data === null || typeof data !== "object") return;
                const event = JSON.stringify({ from: socket.userID, groupID: data.groupID == null ? null : Number(data.groupID), typing: data.typing === true });
                if (data.groupID != null) {
                    if (!(await messaging.isMember(Number(data.groupID), socket.userID))) return;
                    const ids = await messaging.audience({ groupID: Number(data.groupID) });
                    emitToUsers(ids.filter(id => id !== socket.userID), "typing", event);
                } else if (Number.isInteger(Number(data.to)) && Number(data.to) !== socket.userID) {
                    emitToUsers([Number(data.to)], "typing", event);
                }
            } catch (e) { /* a malformed typing event is ignored */ }
        });

        socket.on("chat message", async (data_raw)=>{
            // Anything a client sends is untrusted: a malformed payload must not crash the whole server.
            let data;
            try {
                data = JSON.parse(data_raw);
            } catch (e) {
                console.info(`client [${socket.id}] sent an invalid message, ignored`);
                return;
            }
            if (data === null || typeof data !== "object") {
                console.info(`client [${socket.id}] sent an incomplete message, ignored`);
                return;
            }
            if (data["sender"] !== undefined && Number(data["sender"]) !== socket.userID) {
                console.info(`client [${socket.id}] (user ${socket.userID}) tried to send as user ${data["sender"]}, ignored`);
                return;
            }
            console.info(`client [${socket.id}] sends: [${data_raw}]`);
            try {
                const result = await messaging.createMessage(socket.userID, data);   // validates, stores, then delivers
                if (result.error) console.info(`message from [${socket.id}] refused: ${result.error}`);
            } catch (e) {
                console.error(e);
            }
        })
    });
    return server;
}

// The schema is brought up to date before anything is served.
const migrate = require("./server/migrate");
(async () => {
    for (let attempt = 1; ; attempt++) {
        try {
            await migrate(dbc);
            break;
        } catch (e) {
            if (attempt >= 10) { console.error("database migration failed:", e); process.exit(1); }
            console.info(`database not ready (${e.code || e.message}), retrying...`);
            await new Promise(r => setTimeout(r, 2000));
        }
    }
    console.info("database schema is up to date.");
    app.listen(port, ()=> console.info("express server listening on port "+port));
    startSocketServer();
})();
