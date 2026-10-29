# ChatApp: Android chat with a Node.js server

A real-time messaging app: an **Android client (Java)** talking to a **Node.js server** (Express + Socket.IO) backed by **MySQL**. Sign up, find people, add friends and chat; messages are delivered instantly and stored in the database.

```
Android app (Java, Volley, Socket.IO client)
      │  HTTP  :8001  sign up / sign in / search / friends / history
      │  WebSocket :8000  live messages
      ▼
Node.js server (Express, Socket.IO, Argon2) ──► MySQL 8 (users, friends, messages)
```

## Features

- Sign up and sign in; the session is kept on the phone until you log out. Passwords are stored as Argon2 hashes.
- **Chats** tab with the list of conversations and the conversation view with live delivery over Socket.IO.
- **Search** tab: full-text search of users by name or e-mail (results ranked by relevance).
- **People** tab: add and remove friends.
- Account screen with your profile and log out.

## Quick start (server and database)

```bash
docker compose up --build        # MySQL 8 with the sample database + the server (ports 8001 and 8000)
npm install && npm run smoke     # 22 end-to-end checks against the running server
```

The sample database (`chatdb.sql`) contains a few test accounts whose passwords are not documented: create your own account from the app or with `POST /signup`.

### Without Docker

Create a MySQL database named `ChatDB`, import `chatdb.sql` into it (phpMyAdmin or `mysql ChatDB < chatdb.sql`), then:

```bash
npm install
npm run server                   # or: npm start
```

| Variable | Default | |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | MySQL address |
| `DB_USER` / `DB_PASSWORD` | `root` / empty | MySQL login |
| `DB_NAME` | `ChatDB` | database name |
| `PORT` | `8001` | HTTP API |
| `SOCKET_PORT` | `8000` | Socket.IO |

## Android app

The app needs the address of the computer running the server. It is a build-time setting, so there is no source to edit:

```bash
nix develop .#android                                      # JDK 17 + Android SDK 33 (or use Android Studio)
./gradlew assembleDebug                                    # emulator: reaches the host at 10.0.2.2 (the default)
./gradlew assembleDebug -PchatServerHost=192.168.1.20      # real phone: your computer's LAN IP
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. In Android Studio, put `chatServerHost=192.168.1.20` in `~/.gradle/gradle.properties`. The phone and the computer must be on the same network. Plain HTTP is allowed (`usesCleartextTraffic`) because the server is meant for a local network.

## HTTP API

All routes are `POST` with a JSON body and answer with JSON; a failure is `{"error": "..."}`.

| Route | Body | Returns |
| --- | --- | --- |
| `/signup` | `userName`, `userEmail`, `userPassword` | `userID` |
| `/signin` | `userEmail`, `userPassword` | `userID`, `userName`, `userEmail` |
| `/user` | `userID` | `userID`, `userName`, `userEmail` |
| `/search` | `searchTable` (`users`), `searchCols` (`userName, userEmail`), `searchTerm` | `results` (up to 100 users) |
| `/friends` | `userID` | `friends` |
| `/friends/add`, `/friends/remove` | `userID1`, `userID2` | empty on success |
| `/chats` | `userID` | `messages`: the last message of each conversation, with the other user |
| `/messages` | `userID1`, `userID2` | `messages`: the conversation |
| `/messages/send` | `sender`, `receiver`, `content`, `date` | `messageID` |

## Real time (Socket.IO)

1. On connection the server emits `init request`; the client answers `init` with its user id.
2. To send, the client emits `chat message` with a JSON string `{"sender", "receiver", "content", "date"}`.
3. The server forwards it to the sender's and the receiver's connected devices as `chat message` and saves it.

Malformed or incomplete payloads are ignored.

## Project layout

```
app/                    Android app (package com.example.chatapp): activities, fragments, adapters
server.js               Express API + Socket.IO server
chatdb.sql              database schema and sample data
scripts/smoke-test.js   end-to-end test of the API and the real-time path
docker-compose.yml      MySQL + server;  Dockerfile builds the server image
flake.nix               Nix dev shells (server, Android SDK)
```

## Tech stack

Android (Java, Volley, Material, Navigation, `socket.io-client`) · Node.js 22 · Express 4 · Socket.IO 4 · MySQL 8 (`mysql2`, full-text search) · Argon2 · Docker Compose · Gradle 7.5 / Android Gradle Plugin 7.4.
