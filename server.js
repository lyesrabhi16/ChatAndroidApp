



const 
    argon2 = require("argon2"),
    express = require('express'),
    app = express(),
    port = 8001,
    dbc = require('mysql').createConnection({
        host:"localhost",
        user:"root",
        password:"",
        database:"ChatDB"
    });

dbc.connect(err => {
        if (err) console.info("failed to connect to DB. ",err);
        else{
            console.info("DB connected.");
        }
});

app.use(express.json());
app.use(require('body-parser').urlencoded({ extended: false }));

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
    
    let
        response = undefined,
        user = {
            userName : req.body["userName"],
            userEmail : req.body["userEmail"],
            userPassword : await argon2.hash(req.body["userPassword"]) 
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
                console.error("email does not exits");
                response = {error : "email does not exits"};
                res.json(response);
            }
            else{
                if (await argon2.verify(result0.userPassword,user.userPassword)){
                    console.info('user signed in');
                    response = {
                        userID    : result0.userID,
                        userEmail : result0.userEmail,
                        userName : result0.userName
                    };
                    res.json(response);
                }
                else{
                    console.error("wrong password");
                    response = {error : "wrong password"};
                    res.json(response);
                }
            }
            
        }
        
    })
});

app.post("/user",(req, res) =>{

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
                };
                console.error("user : " ,response);
                res.json(response);
            }
            
        }
        
    })
});

app.post("/search",(req, res) => {
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

    let 
        response = undefined,
        table = req.body["searchTable"],
        cols = req.body["searchCols"],
        term = req.body["searchTerm"],
        termWords = term.split(" "),
        sql = `SELECT t.userID, t.userName, t.userEmail FROM
            (SELECT *,
             MATCH(${cols}) Against('"${term}"' in boolean mode) score_exact,
             MATCH(${cols}) Against('${termWords.map(w => w = "+"+w).toString().replace(",", " ")}' in boolean mode) score_all,
             MATCH(${cols}) Against('${termWords.toString().replace(",", " ")}' in boolean mode) score_some,
             MATCH(${cols}) Against('${termWords.map(w => w = w+"*").toString().replace(",", " ")}' in boolean mode) score_pre
             FROM  (
                SELECT userID, userName, userEmail FROM (
                    SELECT * FROM ${table} WHERE (
                        ${termWords.map((term) => cols.split(",")
                            .map((w)=> w = "CONVERT("+w+" USING utf8) LIKE '%"+term+"%'")
                                                            .toString().replace(",", " OR "))
                                                                                    .toString().replace(",", " OR ")})
                ) as t
            ) as f
             ORDER BY score_exact DESC, score_all DESC, score_some DESC, score_pre DESC
            ) as t`.replace(/\n/g, " ");
    

    dbc.query(sql, (err, results)=>{
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

app.post("/chats", (req, res)=>{
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
                            sender = '${userID}'
                            OR
                            receiver = '${userID}'
                    ) as r
                WHERE userID = r.sender OR userID = r.receiver
                ORDER BY messages.date DESC, messageID DESC
            `.replace(/\n/g, " ");
    
    dbc.query(sql, (err, results)=>{
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

app.post("/messages", (req, res)=>{
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
        userID1 = req.body["userID1"],
        userID2 = req.body["userID2"],
        sql = "SELECT * FROM messages WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) ORDER By date ASC";
    
    dbc.query(
        sql,
        [userID1,userID2,userID2,userID1],
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

app.post("/messages/send", (req, res)=>{
    if(req.body["sender"] === undefined){
        let message = "missing parameter : sender"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["receiver"] === undefined){
        let message = "missing parameter : receiver"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["content"] === undefined){
        let message = "missing parameter : content"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }
    if(req.body["date"] === undefined){
        let message = "missing parameter : date"
        console.error(message);
        res.json(
            {error : message}
        );
        return;
    }

    let
        response = undefined;
        message = {
            sender : req.body["sender"],
            receiver : req.body["receiver"],
            content : req.body["content"],
            date : req.body["date"]
        },
        sql = "INSERT INTO messages SET ?";
    
    dbc.query(
        sql,
        message,
        (err, result) => {
            if (err){
                response = {error : ""+ err.code};
                res.json(response);
            }
            else {               
                response = {
                    messageID : result.insertId
                };
                res.json(response);
            
            }
        }
        
        )

    
});

app.post("/friends", (req, res)=>{
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
        sql = `SELECT users.userID, users.userName, users.userEmail 
                FROM users,
                    (
                        select * from friends WHERE user1 = ${userID} OR user2 = ${userID}
                    )  as f
                where (userID = f.user1 OR userID = f.user2) AND userID != ${userID}
            `.replace(/\n/g," ");
    
    dbc.query(
        sql,
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


app.post("/friends/add", (req, res)=>{
    
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

app.post("/friends/remove", (req, res)=>{
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

app.listen(port, ()=> console.info("express server listening on port "+port));




//server
const {Server} = require("socket.io"),
    server = new Server(8000);

// clients is an array of client objects
// client = { 
//      socket : ?,
//      userID : ?
// }
let clients = new Set();

server.on("connection", (socket) => {

    console.info(`Connection detected : [${socket.id}]`)
    
    socket.emit("message","hello from server");
    socket.emit("init request");
    socket.on("init", (id)=>{

        let client = {
            socket : socket,
            userID : id
        };
        clients.add(client);
        console.info("Client connected : " + `[${client.userID}, ${client.socket.id}]`);
        console.info("All connected clients :");
        clients.forEach((client) => console.info(`\n\t[${client.userID} ,${client.socket.id}]`));
        console.info("\n");
    });

    socket.on("disconnect", () => {
        console.info(`Client disconnected [id=${socket.id}]`);
        clients.forEach((client) => {
            if (client.socket.id === socket.id) clients.delete(client);
        })
    });
    
    socket.on("message", (msg) => {
        console.info("Client ["+ socket.id +"] sends : ["+ msg +"]");
    })

    socket.on("chat message", (data_raw)=>{
    
        let data = JSON.parse(data_raw);
        console.info(`client [${socket.id}] sends: [${data_raw}]`);
        clients.forEach((client) => {

            if(client.userID == data["receiver"] || client.userID == data["sender"])
            {
                console.info(`\t[${data["content"]}] sent to : client [${client.socket.id}]`);    
                client.socket.emit("chat message", data_raw);
            }
        });
        let sql = "INSERT INTO messages SET ?";
        dbc.query(
            sql,
            data,
            (err, result) => {
                if (err){
                    console.info(`failed to add message to DB.\nerror: `+err.code);
                }
                else {               
                    console.info(`message added. [messageID = ${result.insertId}]`);
                }
            }
        )
    })

});




///client
/* const express = require("express");

const app = express();
const port = 3000;

app.get("/", (req, res) => {
    const
        io = require("socket.io-client"),
        ioClient = io.connect("http://localhost:8000");

    ioClient.on("message", (msg) => {
        console.info(msg)
        ioClient.emit("message", "hello from client");
    });

})
app.listen(port, ()=>{
    console.info("express server listening on port " + port);
}); */