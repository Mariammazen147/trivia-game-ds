# Assignment 1 — Multiplayer Trivia Game
## Group Information

| Field | Value |
|-------|-------|
| Course | Distributed Systems — Winter 2026 |
| Group Number | G7 |
| Mariam Mazen | 20226131 |
| Eman Hussein | 20226023 |

---

## How to Run

You need Java JDK 17 or higher and the file gson-2.10.1.jar inside a lib/ folder.

Compile on Windows:
```
javac -cp "lib/gson-2.10.1.jar" -d out server\Server.java server\ClientHandler.java server\GameRoom.java server\GameConfig.java server\QuestionManager.java server\UserManager.java server\ScoreManager.java server\Question.java server\User.java server\ScoreEntry.java client\Client.java
```

Compile on Mac or Linux:
```
javac -cp "lib/gson-2.10.1.jar" -d out server/Server.java server/ClientHandler.java server/GameRoom.java server/GameConfig.java server/QuestionManager.java server/UserManager.java server/ScoreManager.java server/Question.java server/User.java server/ScoreEntry.java client/Client.java
```

Start the server:
```
java -cp "out;lib/gson-2.10.1.jar" server.Server
```
On Mac/Linux replace ; with :

Start a client (open a new terminal for each player):
```
java -cp "out;lib/gson-2.10.1.jar" client.Client localhost 5000
```
On Mac/Linux replace ; with :

Two accounts are already set up for testing. Username alice and username bob, both with password password123.

---

## Project Files
```
trivia-game-ds/
├── server/
│   ├── Server.java          — starts the server, loads all data files, accepts connections
│   ├── ClientHandler.java   — one per connected player, handles login, menu, and game input
│   ├── GameRoom.java        — runs the game, sends questions, manages timer and scoring
│   ├── QuestionManager.java — loads questions.json, filters by category and difficulty
│   ├── UserManager.java     — handles login and registration, stores hashed passwords
│   ├── ScoreManager.java    — reads and writes scores.json
│   ├── GameConfig.java      — reads config.json
│   ├── User.java            — stores user data
│   ├── Question.java        — stores question data
│   └── ScoreEntry.java      — stores one game result
├── client/
│   └── Client.java          — connects to server, displays messages, sends player input
├── data/
│   ├── config.json          — port number, timer, team size limits, warning times
│   ├── questions.json       — 28 questions in 4 categories, 3 difficulty levels
│   ├── users.json           — saved user accounts
│   └── scores.json          — saved score history per player
└── lib/
    └── gson-2.10.1.jar      — used to read and write JSON files
```

---

## Architecture

The server handles everything. The client is just a terminal that shows what the server sends and forwards what the player types. All game logic runs on the server.

Multiple players are handled using threads. When a player connects, the server gives them their own thread using a thread pool so players don't wait on each other. We tested with 4 clients at the same time.

The server and client communicate using plain text over TCP. Every message starts with a prefix like MSG: or QUESTION: or ERROR: so the client knows how to display it. The client runs two threads, one for showing incoming server messages and one for reading keyboard input, so timer warnings appear even while the player is typing.

---

## Features

**1. Multiple clients**
We used Executors.newCachedThreadPool() in Server.java. Each player that connects gets their own thread. They run completely independently.

**2. Question bank**
QuestionManager loads questions from questions.json on startup. It can filter by category and difficulty. Results are shuffled so you get different questions each game. If you request more questions than exist for that filter, you just get however many are available.

**3. Login and registration**
Passwords are hashed with SHA-256 before saving. We never store the real password. At login, we hash what was typed and compare. Wrong username gives error 404, wrong password gives 401, duplicate username at registration gives 409. The duplicate check is case-insensitive so alice and ALICE are treated as the same.

**4. File loading**
Server.java loads all four files before opening the server socket. If any file fails, it prints an error and stops. It won't start with missing data.

**5. Game menu**
After login, players see a numbered menu. Picking solo or creating a team room goes through multiple steps (category, difficulty, count) one at a time. The server tracks which step you're on using a sub-state variable.

**6. Teams**
A player creates a room and gets a room ID. Others join by entering that ID. Before starting, the server checks that Team 2 has players and both teams are the same size. If not, an error is shown and the game doesn't start.

**7. Game loop**
Questions go to all players at the same time. Players have 15 seconds to answer. If everyone answers early, scoring happens right away without waiting for the timer. Each player can only answer once. Late answers are ignored. After scoring, results are shown to everyone and the next question starts 3 seconds later.

**8. Timer warnings**
The server sends time remaining messages at 10, 5, and 3 seconds left to all players.

**9. Game over**
After the last question everyone sees the final leaderboard. In team mode it also shows both team scores and who won.

**10. Score history**
Scores are saved to scores.json after every game. Players can view their history from the main menu. Correct answer count is tracked separately during the game so it's always accurate.

**11. Error handling**
If a player disconnects, they are removed from the game and remaining players are told. If the room becomes empty it is deleted. Wrong input shows an error and the menu again. Answers after the timer expired are ignored silently.

---

## Decisions

- Solo game: 100 points per correct answer, no speed bonus since there is no one to race.
- Team game: 100 points per correct answer, plus 50 extra for whoever answered first.
- JSON was used for all data files because it is easy to read and check manually.
- Game rooms only exist while the server is running. Accounts and scores survive restarts.
- If a player disconnects mid-game, the game continues for the others.
- The quit option is - as required.

---

## Known Limitations

- A player who disconnects mid-game cannot rejoin.
- Joining a room requires typing the full room ID manually.
- If you request more questions than exist for a category and difficulty, you get fewer than requested with no warning.