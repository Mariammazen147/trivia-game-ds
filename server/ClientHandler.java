package server;

import com.google.gson.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    // --- Top-level States ---
    private static final int STATE_AUTH     = 0;
    private static final int STATE_MENU     = 1;
    private static final int STATE_WAITING  = 2;
    private static final int STATE_IN_GAME  = 3;

    // --- Auth sub-states ---
    private static final int AUTH_IDLE              = 0;
    // Login flow
    private static final int AUTH_LOGIN_USERNAME    = 1;
    private static final int AUTH_LOGIN_PASSWORD    = 2;
    // Register flow
    private static final int AUTH_REG_NAME          = 3;
    private static final int AUTH_REG_USERNAME      = 4;
    private static final int AUTH_REG_PASSWORD      = 5;

    // --- Menu sub-states (for multi-step prompts) ---
    private static final int MSUB_NONE            = 0;
    private static final int MSUB_SP_CATEGORY     = 1;
    private static final int MSUB_SP_DIFFICULTY   = 2;
    private static final int MSUB_SP_COUNT        = 3;
    private static final int MSUB_TC_NAME         = 4;
    private static final int MSUB_TC_CATEGORY     = 5;
    private static final int MSUB_TC_DIFFICULTY   = 6;
    private static final int MSUB_TC_COUNT        = 7;
    private static final int MSUB_JOIN_ROOM       = 8;

    private final Socket socket;
    private final UserManager userManager;
    private final QuestionManager questionManager;
    private final ScoreManager scoreManager;
    private final ConcurrentHashMap<String, GameRoom> gameRooms;
    private final GameConfig config;

    private PrintWriter out;
    private BufferedReader in;

    // Current top-level state
    int state = STATE_AUTH;

    private User currentUser = null;
    GameRoom currentRoom = null;
    private boolean isTeamCreator = false;

    // Auth flow
    private int authState = AUTH_IDLE;
    private String regName;
    private String regUsername;
    private String loginUsername;

    // Menu sub-state for multi-step prompts
    private int menuSub = MSUB_NONE;
    private String tmpCategory;
    private String tmpDifficulty;
    private String tmpRoomName;

    private final Gson gson = new Gson();

    public ClientHandler(Socket socket, UserManager userManager, QuestionManager questionManager,
                         ScoreManager scoreManager, ConcurrentHashMap<String, GameRoom> gameRooms,
                         GameConfig config) {
        this.socket = socket;
        this.userManager = userManager;
        this.questionManager = questionManager;
        this.scoreManager = scoreManager;
        this.gameRooms = gameRooms;
        this.config = config;
    }

    public String getUsername() {
        return currentUser != null ? currentUser.username : "unknown";
    }

    public synchronized void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            sendMessage("MSG:Welcome to Trivia Game!");
            showAuthMenu();

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                dispatch(line);
            }
        } catch (IOException e) {
            // Client disconnected
        } finally {
            cleanup();
        }
    }

    private void dispatch(String line) {
        switch (state) {
            case STATE_AUTH:    handleAuth(line);    break;
            case STATE_MENU:    handleMenu(line);    break;
            case STATE_WAITING: handleWaiting(line); break;
            case STATE_IN_GAME: handleInGame(line);  break;
        }
    }

    // =========================================================================
    // AUTH STATE
    // =========================================================================

    private void showAuthMenu() {
        sendMessage("MENU:1. Login");
        sendMessage("MENU:2. Register");
        sendMessage("MENU:Enter your choice:");
    }

    private void handleAuth(String line) {
        // Route to active sub-flow first
        if (authState != AUTH_IDLE) {
            handleAuthStep(line);
            return;
        }

        switch (line.trim()) {
            case "1":
                authState = AUTH_LOGIN_USERNAME;
                sendMessage("MSG:Enter your username:");
                break;
            case "2":
                authState = AUTH_REG_NAME;
                sendMessage("MSG:--- Account Registration ---");
                sendMessage("MSG:Enter your full name:");
                break;
            default:
                sendMessage("ERROR:Please enter 1 to login or 2 to register.");
                showAuthMenu();
                break;
        }
    }

    private void handleAuthStep(String line) {
        switch (authState) {
            case AUTH_LOGIN_USERNAME:
                loginUsername = line.trim();
                if (loginUsername.isEmpty()) {
                    sendMessage("ERROR:Username cannot be empty:");
                    return;
                }
                authState = AUTH_LOGIN_PASSWORD;
                sendMessage("MSG:Enter your password:");
                break;

            case AUTH_LOGIN_PASSWORD:
                User user = userManager.login(loginUsername, line.trim());
                authState = AUTH_IDLE;
                if (user == null) {
                    sendMessage("ERROR:Invalid username or password.");
                    showAuthMenu();
                } else {
                    currentUser = user;
                    state = STATE_MENU;
                    sendMessage("MSG:Login successful! Welcome back, " + user.name + ".");
                    showMenu();
                }
                break;

            case AUTH_REG_NAME:
                regName = line.trim();
                if (regName.isEmpty()) {
                    sendMessage("ERROR:Name cannot be empty:");
                    return;
                }
                authState = AUTH_REG_USERNAME;
                sendMessage("MSG:Choose a username:");
                break;

            case AUTH_REG_USERNAME:
                regUsername = line.trim();
                if (regUsername.isEmpty()) {
                    sendMessage("ERROR:Username cannot be empty:");
                    return;
                }
                authState = AUTH_REG_PASSWORD;
                sendMessage("MSG:Choose a password:");
                break;

            case AUTH_REG_PASSWORD:
                String password = line.trim();
                if (password.isEmpty()) {
                    sendMessage("ERROR:Password cannot be empty:");
                    return;
                }
                String error = userManager.register(regName, regUsername, password);
                authState = AUTH_IDLE;
                if (error != null) {
                    sendMessage("ERROR:" + error);
                    showAuthMenu();
                } else {
                    currentUser = userManager.getUser(regUsername);
                    state = STATE_MENU;
                    sendMessage("MSG:Registration successful! Welcome, " + regName + ".");
                    showMenu();
                }
                break;
        }
    }

    // =========================================================================
    // MENU STATE
    // =========================================================================

    private void showMenu() {
        sendMessage("MENU:========== Main Menu ==========");
        sendMessage("MENU:1. Play Solo");
        sendMessage("MENU:2. Create Team Room");
        sendMessage("MENU:3. Join Team Room");
        sendMessage("MENU:4. View My Scores");
        sendMessage("MENU:-. Quit");
        sendMessage("MENU:Enter your choice:");
    }

    private void handleMenu(String line) {
        // If we are in a multi-step menu sub-flow, route there
        if (menuSub != MSUB_NONE) {
            handleMenuSubState(line);
            return;
        }

        // Strip optional CHOICE: prefix
        String choice = line.startsWith("CHOICE:") ? line.substring(7).trim() : line.trim();

        switch (choice) {
            case "1":
                menuSub = MSUB_SP_CATEGORY;
                promptSoloCategory();
                break;
            case "2":
                menuSub = MSUB_TC_NAME;
                sendMessage("MSG:--- Create Team Room ---");
                sendMessage("MSG:Enter a name for your room:");
                break;
            case "3":
                showAvailableRooms();
                break;
            case "4":
                showScores();
                break;
            case "-":
                sendMessage("MSG:Goodbye! Thanks for playing.");
                cleanup();
                break;
            default:
                sendMessage("ERROR:Invalid choice. Please enter 1, 2, 3, 4, or -");
                showMenu();
                break;
        }
    }

    private void handleMenuSubState(String line) {
        switch (menuSub) {
            // ---- Solo setup ----
            case MSUB_SP_CATEGORY:
                tmpCategory = resolveCategory(line);
                menuSub = MSUB_SP_DIFFICULTY;
                sendDifficultyMenu();
                break;

            case MSUB_SP_DIFFICULTY:
                tmpDifficulty = resolveDifficulty(line);
                menuSub = MSUB_SP_COUNT;
                sendMessage("MSG:How many questions would you like? (1-20):");
                break;

            case MSUB_SP_COUNT:
                try {
                    int count = Integer.parseInt(line.trim());
                    if (count < 1 || count > 20) {
                        sendMessage("ERROR:Please enter a number between 1 and 20:");
                        return;
                    }
                    menuSub = MSUB_NONE;
                    launchSoloGame(tmpCategory, tmpDifficulty, count);
                } catch (NumberFormatException e) {
                    sendMessage("ERROR:Please enter a valid number between 1 and 20:");
                }
                break;

            // ---- Team create setup ----
            case MSUB_TC_NAME:
                tmpRoomName = line.trim();
                if (tmpRoomName.isEmpty()) {
                    sendMessage("ERROR:Room name cannot be empty. Enter a room name:");
                    return;
                }
                menuSub = MSUB_TC_CATEGORY;
                promptTeamCategory();
                break;

            case MSUB_TC_CATEGORY:
                tmpCategory = resolveCategory(line);
                menuSub = MSUB_TC_DIFFICULTY;
                sendDifficultyMenu();
                break;

            case MSUB_TC_DIFFICULTY:
                tmpDifficulty = resolveDifficulty(line);
                menuSub = MSUB_TC_COUNT;
                sendMessage("MSG:How many questions? (1-20):");
                break;

            case MSUB_TC_COUNT:
                try {
                    int count = Integer.parseInt(line.trim());
                    if (count < 1 || count > 20) {
                        sendMessage("ERROR:Please enter a number between 1 and 20:");
                        return;
                    }
                    menuSub = MSUB_NONE;
                    createTeamRoom(tmpRoomName, tmpCategory, tmpDifficulty, count);
                } catch (NumberFormatException e) {
                    sendMessage("ERROR:Please enter a valid number between 1 and 20:");
                }
                break;

            // ---- Join room ----
            case MSUB_JOIN_ROOM:
                handleJoinRoomInput(line);
                break;

            default:
                menuSub = MSUB_NONE;
                showMenu();
                break;
        }
    }

    private void promptSoloCategory() {
        sendMessage("MSG:--- Solo Game Setup ---");
        sendCategoryMenu();
    }

    private void promptTeamCategory() {
        sendCategoryMenu();
    }

    private void sendCategoryMenu() {
        List<String> cats = questionManager.getAvailableCategories();
        sendMessage("MSG:Select a category:");
        for (int i = 0; i < cats.size(); i++) {
            sendMessage("MSG:" + (i + 1) + ". " + cats.get(i));
        }
        sendMessage("MSG:" + (cats.size() + 1) + ". Any");
    }

    private void sendDifficultyMenu() {
        sendMessage("MSG:Select a difficulty:");
        sendMessage("MSG:1. Easy");
        sendMessage("MSG:2. Medium");
        sendMessage("MSG:3. Hard");
        sendMessage("MSG:4. Any");
    }

    private String resolveCategory(String input) {
        List<String> cats = questionManager.getAvailableCategories();
        try {
            int n = Integer.parseInt(input.trim());
            if (n >= 1 && n <= cats.size()) return cats.get(n - 1);
            if (n == cats.size() + 1) return "Any";
        } catch (NumberFormatException ignored) {}
        return input.trim(); // accept typed name as-is
    }

    private String resolveDifficulty(String input) {
        switch (input.trim()) {
            case "1": return "easy";
            case "2": return "medium";
            case "3": return "hard";
            case "4": return "Any";
            default:  return input.trim();
        }
    }

    private void launchSoloGame(String category, String difficulty, int count) {
        String roomId = "solo-" + currentUser.username + "-" + System.currentTimeMillis();
        GameRoom room = new GameRoom(roomId, "Solo-" + currentUser.username, false,
                category, difficulty, count, questionManager, config, scoreManager);
        gameRooms.put(roomId, room);
        room.addPlayer(this, 1);
        currentRoom = room;
        state = STATE_IN_GAME;
        sendMessage("MSG:Starting solo game! Good luck, " + currentUser.name + "!");
        sendMessage("MSG:Answer each question with ANSWER:<A/B/C/D>");
        room.startGame();
    }

    private void createTeamRoom(String roomName, String category, String difficulty, int count) {
        String roomId = "team-" + UUID.randomUUID().toString().substring(0, 8);
        GameRoom room = new GameRoom(roomId, roomName, true,
                category, difficulty, count, questionManager, config, scoreManager);
        gameRooms.put(roomId, room);
        room.addPlayer(this, 1);
        currentRoom = room;
        isTeamCreator = true;
        state = STATE_WAITING;
        sendMessage("MSG:Room '" + roomName + "' created!");
        sendMessage("MSG:Room ID: " + roomId + "  (share this with your opponents)");
        sendMessage("MSG:You are on Team 1.");
        sendMessage("MSG:When ready, type CHOICE:START to begin the game.");
        sendMessage("MSG:Others can join with option 3 from the main menu.");
    }

    private void showAvailableRooms() {
        List<GameRoom> available = new ArrayList<>();
        for (GameRoom r : gameRooms.values()) {
            if (r.isTeamMode() && !r.isGameStarted() && !r.isGameOver()) {
                available.add(r);
            }
        }
        if (available.isEmpty()) {
            sendMessage("MENU:No team rooms are currently available.");
            sendMessage("MENU:Choose '2' to create one, or any other option:");
            showMenu();
            return;
        }
        sendMessage("MENU:--- Available Team Rooms ---");
        for (GameRoom r : available) {
            sendMessage("MENU:[" + r.getRoomId() + "] " + r.getRoomInfo());
        }
        sendMessage("MENU:Enter a Room ID to join, or 'back' to return to the main menu:");
        menuSub = MSUB_JOIN_ROOM;
    }

    private void handleJoinRoomInput(String input) {
        menuSub = MSUB_NONE;
        if (input.equalsIgnoreCase("back")) {
            showMenu();
            return;
        }
        GameRoom room = gameRooms.get(input.trim());
        if (room == null || !room.isTeamMode() || room.isGameStarted() || room.isGameOver()) {
            sendMessage("ERROR:Room not found or no longer available. Please try again.");
            showMenu();
            return;
        }
        boolean joined = room.addPlayer(this, 2);
        if (!joined) {
            sendMessage("ERROR:Could not join room — Team 2 may be full.");
            showMenu();
            return;
        }
        currentRoom = room;
        isTeamCreator = false;
        state = STATE_WAITING;
        sendMessage("MSG:You joined room '" + room.getRoomName() + "' on Team 2!");
        sendMessage("MSG:Waiting for the room creator to start the game...");
        room.broadcast("MSG:" + currentUser.name + " joined Team 2! (Team 2 size: " + room.getTeam2Size() + ")");
    }

    // =========================================================================
    // WAITING STATE
    // =========================================================================

    private void handleWaiting(String line) {
        String choice = line.startsWith("CHOICE:") ? line.substring(7).trim() : line.trim();

        if (choice.equalsIgnoreCase("START")) {
            if (!isTeamCreator) {
                sendMessage("ERROR:Only the room creator can start the game.");
                return;
            }
            if (currentRoom == null) {
                sendMessage("ERROR:You are not in a room.");
                return;
            }
            // Move all players to IN_GAME
            for (ClientHandler p : currentRoom.getAllPlayers()) {
                p.state = STATE_IN_GAME;
            }
            currentRoom.startGame();

        } else if (choice.equalsIgnoreCase("LEAVE") || choice.equals("-")) {
            leaveRoom();

        } else if (choice.equalsIgnoreCase("STATUS")) {
            if (currentRoom != null) {
                sendMessage("MSG:Room: " + currentRoom.getRoomName()
                        + " | Team1: " + currentRoom.getTeam1Size()
                        + " | Team2: " + currentRoom.getTeam2Size());
            }
        } else {
            sendMessage("MSG:Waiting for the game to start.");
            if (isTeamCreator) {
                sendMessage("MSG:Type CHOICE:START to begin, CHOICE:STATUS to check players, or CHOICE:LEAVE to leave.");
            } else {
                sendMessage("MSG:Type CHOICE:LEAVE to leave the room.");
            }
        }
    }

    private void leaveRoom() {
        if (currentRoom != null) {
            currentRoom.removePlayer(this);
            String name = currentUser != null ? currentUser.name : "A player";
            currentRoom.broadcast("MSG:" + name + " has left the room.");
            if (currentRoom.getTeam1Size() == 0 && currentRoom.getTeam2Size() == 0) {
                gameRooms.remove(currentRoom.getRoomId());
            }
            currentRoom = null;
        }
        isTeamCreator = false;
        state = STATE_MENU;
        sendMessage("MSG:You left the room.");
        showMenu();
    }

    // =========================================================================
    // IN-GAME STATE
    // =========================================================================

    private void handleInGame(String line) {
        if (line.toUpperCase().startsWith("ANSWER:")) {
            String answer = line.substring(7).trim();
            if (answer.isEmpty()) {
                sendMessage("ERROR:Please provide an answer. Usage: ANSWER:<A/B/C/D>");
                return;
            }
            if (currentRoom != null && !currentRoom.isGameOver()) {
                currentRoom.submitAnswer(currentUser.username, answer);
                sendMessage("MSG:Your answer '" + answer.toUpperCase() + "' has been recorded.");
            } else {
                sendMessage("MSG:Game is not active.");
            }
        } else {
            sendMessage("MSG:Game in progress! Submit your answer with: ANSWER:<A/B/C/D>");
        }
    }

    /**
     * Called by GameRoom after the game ends to return this client to the main menu.
     */
    public void returnToMenu() {
        state = STATE_MENU;
        menuSub = MSUB_NONE;
        currentRoom = null;
        isTeamCreator = false;
        sendMessage("MSG:Game over! Returning to main menu...");
        showMenu();
    }

    // =========================================================================
    // SCORES
    // =========================================================================

    private void showScores() {
        if (currentUser == null) return;
        List<ScoreEntry> entries = scoreManager.getScores(currentUser.username);
        if (entries.isEmpty()) {
            sendMessage("MSG:You have no scores recorded yet. Play a game to see your scores!");
        } else {
            String json = gson.toJson(entries);
            sendMessage("SCORES:" + json);
        }
        showMenu();
    }

    // =========================================================================
    // CLEANUP
    // =========================================================================

    public void cleanup() {
        if (currentRoom != null) {
            currentRoom.removePlayer(this);
            String name = currentUser != null ? currentUser.name : "A player";
            currentRoom.broadcast("MSG:" + name + " disconnected.");
            if (currentRoom.getTeam1Size() == 0 && currentRoom.getTeam2Size() == 0) {
                gameRooms.remove(currentRoom.getRoomId());
            }
            currentRoom = null;
        }
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }
}
