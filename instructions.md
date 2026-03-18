# Task: Write the Submission Document

Look at all the Java source files in this project and write a submission document.
Save it as `Assign1_Documentation.md` (I will convert it to PDF or Word later).

---

## Document Structure to Follow

Write the document with these exact sections:

---

### 1. Group Information

| Field | Value |
|-------|-------|
| Course | Distributed Systems — Winter 2026 |
| Assignment | Assignment 1: Multiplayer Trivia Game |
| Group Number | [G7] |
| Mariam Mazen  | [20226131] |
| EMan Hussein | [20226023] |

---

### 2. How to Run

#### Requirements
- Java JDK 17 or later (project developed and tested on OpenJDK 21)
- Gson library: `gson-2.10.1.jar` placed in `lib/` folder

#### Compile
```bash
javac -cp "lib/gson-2.10.1.jar" -d out $(find . -name "*.java")
```

#### Start the Server
```bash
java -cp "out:lib/gson-2.10.1.jar" server.Server
```
The server loads all data files on startup and listens on port 5000 (configurable in `data/config.json`).

#### Start a Client (run this in a new terminal for each player)
```bash
java -cp "out:lib/gson-2.10.1.jar" client.Client localhost 5000
```

> On Windows replace `:` with `;` in the classpath.

---

### 3. Project Structure

List and briefly explain every source file. Look at the actual files in the project and describe what each one does. Format like this:

```
trivia-game/
├── server/
│   ├── Server.java           — [what it does]
│   ├── ClientHandler.java    — [what it does]
│   ...
├── client/
│   └── Client.java           — [what it does]
├── data/
│   ├── users.json            — [what it stores]
│   ├── scores.json           — [what it stores]
│   ├── questions.json        — [what it stores]
│   └── config.json           — [what it stores]
```

---

### 4. Architecture Overview

Write 2–3 paragraphs explaining:
- The client-server model used (socket-based, one thread per client)
- How the server handles multiple clients simultaneously (thread model)
- How the client communicates with the server (message protocol/format)

---

### 5. Features Implemented

Go through each numbered feature from the assignment and write one short paragraph per feature explaining HOW it was implemented (not just that it was done). Reference actual class names and method names from the code.

Features to cover:
1. Multiple concurrent clients
2. Question bank management
3. User authentication (login, register, error codes 401/404/409)
4. Game setup and file loading on startup
5. Game options menu
6. Teams (creation, joining, equal size validation)
7. The game loop (broadcasting questions, timers, answer evaluation)
8. Time updates (countdown warnings broadcast to clients)
9. End of game summary
10. Score history (persistence across sessions)
11. Error handling (disconnections, invalid input, late answers)

---

### 6. Decisions and Assumptions

Write one bullet point per decision. Look at the actual implementation and document what choices were made. Include things like:

- What score calculation formula was used (e.g., points per correct answer, speed bonus)
- How passwords are stored (e.g., SHA-256 hashing)
- What file format is used for data (e.g., JSON via Gson)
- How game rooms work (in-memory only, not persisted)
- What happens when a client disconnects mid-game
- How team formation works (who creates, who joins, when game starts)
- What the quit character is (`-`)
- Any edge cases you decided to handle a specific way
- Any features that were simplified or approximated due to time

---

### 7. Known Limitations (if any)

If there is anything that is partially implemented or has a known bug, list it honestly here. One line per item. If everything works, write "None."

---

## Important Instructions

- Write in clear, professional English
- Keep each section concise — this is a technical document, not an essay
- Reference actual class and method names from the real code
- Do NOT make up features that are not implemented
- If something was not implemented, say so honestly in section 7
- The document should reflect the actual state of the code