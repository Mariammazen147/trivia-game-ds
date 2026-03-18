package server;

import com.google.gson.*;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHandler implements Runnable {

    // level states
    private static final int STATE_AUTH = 0;
    private static final int STATE_MENU = 1;
    private static final int STATE_WAITING = 2;
    private static final int STATE_IN_GAME = 3;

    // auth state
    private static final int AUTH_IDLE = 0;
    // login flow
    private static final int AUTH_LOGIN_USERNAME = 1;
    private static final int AUTH_LOGIN_PASSWORD = 2;
    // register flow
    private static final int AUTH_REG_NAME = 3;
    private static final int AUTH_REG_USERNAME = 4;
    private static final int AUTH_REG_PASSWORD = 5;

    // menu states
    private static final int MSUB_NONE = 0;
    private static final int MSUB_SP_CATEGORY = 1;
    private static final int MSUB_SP_DIFFICULTY = 2;
    private static final int MSUB_SP_COUNT = 3;
    private static final int MSUB_TC_NAME = 4;
    private static final int MSUB_TC_CATEGORY = 5;
    private static final int MSUB_TC_DIFFICULTY = 6;
    private static final int MSUB_TC_COUNT = 7;
    private static final int MSUB_JOIN_ROOM = 8;

    private final Socket socket;
    private final UserManager userManager;
    private final QuestionManager questionManager;
    private final ScoreManager scoreManager;
    private final ConcurrentHashMap<String, GameRoom> gameRooms;
    private final GameConfig config;

    private PrintWriter out;
    private BufferedReader in;

    int state = STATE_AUTH;// current level

    private User currentUser = null;
    GameRoom currentRoom = null;
    private boolean isTeamCreator = false;

    // auth flow
    private int authState = AUTH_IDLE;
    private String regName;
    private String regUsername;
    private String loginUsername;

    // menu flow
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

            sendMessage("MSG:hey!! welcome to the trivia game :)");
            showAuthMenu();

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                dispatch(line);
            }
        } catch (IOException e) {
        } finally {
            cleanup();
        }
    }

    private void dispatch(String line) {
        switch (state) {
            case STATE_AUTH:
                handleAuth(line);
                break;
            case STATE_MENU:
                handleMenu(line);
                break;
            case STATE_WAITING:
                handleWaiting(line);
                break;
            case STATE_IN_GAME:
                handleInGame(line);
                break;
        }
    }

    // auth
    private void showAuthMenu() {
        sendMessage("MENU:1. Login");
        sendMessage("MENU:2. Register");
        sendMessage("MENU:Enter your choice:");
    }

    private void handleAuth(String line) {
        if (authState != AUTH_IDLE) {
            handleAuthStep(line);
            return;
        }
        if (line.isEmpty()) {
            showAuthMenu();
            return;
        }

        switch (line.trim()) {
            case "1":
                authState = AUTH_LOGIN_USERNAME;
                sendMessage("MSG:Enter your username:");
                break;
            case "2":
                authState = AUTH_REG_NAME;
                sendMessage("MSG:Account Registration");
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
                authState = AUTH_LOGIN_PASSWORD;
                sendMessage("MSG:Enter your password:");
                break;

            case AUTH_LOGIN_PASSWORD:
                authState = AUTH_IDLE;
                try {
                    User user = userManager.login(loginUsername, line.trim());
                    currentUser = user;
                    state = STATE_MENU;
                    sendMessage("MSG:Login successful!! welcome back,  " + user.name);
                    showMenu();
                } catch (UserManager.LoginException e) {
                    String code = e.getMessage();
                    if (code.equals("404")) {
                        sendMessage("ERROR:404  Username not found.");
                    } else if (code.equals("401")) {
                        sendMessage("ERROR:401  Wrong password.");
                    } else {
                        sendMessage("ERROR:Login failed.");
                    }
                    showAuthMenu();
                }
                break;

            case AUTH_REG_NAME:
                regName = line.trim();
                authState = AUTH_REG_USERNAME;
                sendMessage("MSG:Choose a username:");
                break;

            case AUTH_REG_USERNAME:
                regUsername = line.trim();
                authState = AUTH_REG_PASSWORD;
                sendMessage("MSG:Choose a password:");
                break;

            case AUTH_REG_PASSWORD:
                String password = line.trim();
                String error = userManager.register(regName, regUsername, password);
                authState = AUTH_IDLE;
                if (error != null) {
                    if (error.equals("Username already taken.")) {
                        sendMessage("ERROR:409 Username already taken.");
                    } else {
                        sendMessage("ERROR:" + error);
                    }
                    showAuthMenu();
                } else {
                    currentUser = userManager.getUser(regUsername);
                    state = STATE_MENU;
                    sendMessage("MSG:account created! welcome " + regName);
                    showMenu();
                }
                break;
        }
    }

    // menu
    private void showMenu() {
        sendMessage("MENU:--- what do you want to do? ---");
        sendMessage("MENU:1. play solo");
        sendMessage("MENU:2. create a team room");
        sendMessage("MENU:3. join a team room");
        sendMessage("MENU:4. view my scores");
        sendMessage("MENU:-. quit");
        sendMessage("MENU:enter your choice:");
    }

    private void handleMenu(String line) {
        if (line.isEmpty()) {
            return;
        }
        if (menuSub != MSUB_NONE) {
            handleMenuSubState(line);
            return;
        }

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
            // solo
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

            // team
            case MSUB_TC_NAME:
                tmpRoomName = line.trim();
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

            // join room
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
        sendMessage("MSG:  SOLO GAME  ");
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
            if (n >= 1 && n <= cats.size())
                return cats.get(n - 1);
            if (n == cats.size() + 1)
                return "Any";
        } catch (NumberFormatException ignored) {
        }
        return input.trim();
    }

    private String resolveDifficulty(String input) {
        switch (input.trim()) {
            case "1":
                return "easy";
            case "2":
                return "medium";
            case "3":
                return "hard";
            case "4":
                return "Any";
            default:
                return input.trim();
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
        sendMessage("MSG:room '" + roomName + "' is ready!");
        sendMessage("MSG:room id: " + roomId + " -- share this with whoever is joining");
        sendMessage("MSG:you're on team 1");
        sendMessage("MSG:type START when everyone is in");
        sendMessage("MSG:teammates can join from the main menu option 3");
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
            sendMessage("MENU:you can create one with option 2:");
            showMenu();
            return;
        }
        sendMessage("MENU:open rooms:");
        for (GameRoom r : available) {
            sendMessage("MENU:[" + r.getRoomId() + "] " + r.getRoomInfo());
        }
        sendMessage("MENU:type the room id to join, or 'back' to go back:");
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
            sendMessage("ERROR:Couldn't join room — Team 2 might be full.");
            showMenu();
            return;
        }
        currentRoom = room;
        isTeamCreator = false;
        state = STATE_WAITING;
        sendMessage("MSG:joined room '" + room.getRoomName() + "', you're on team 2");
        sendMessage("MSG:waiting for the room creator to start...");
        room.broadcast("MSG:" + currentUser.name + " joined Team 2!, Team 2 size: " + room.getTeam2Size());
    }

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
            int team1Size = currentRoom.getTeam1Size();
            int team2Size = currentRoom.getTeam2Size();
            if (team2Size == 0) {
                sendMessage("ERROR:Can't start, no one has joined team 2 yet.");
                return;
            }
            if (team1Size != team2Size) {
                sendMessage("ERROR:teams aren't equal, team 1 has " + team1Size + " and team 2 has " + team2Size);
                return;
            }
            for (ClientHandler p : currentRoom.getAllPlayers()) {
                p.state = STATE_IN_GAME;
            }
            currentRoom.startGame();

        } else if (choice.equalsIgnoreCase("LEAVE") || choice.equals("-")) {
            leaveRoom();

        } else if (choice.equalsIgnoreCase("STATUS")) {
            if (currentRoom != null) {
                sendMessage("MSG:Room: " + currentRoom.getRoomName()
                        + " | team1: " + currentRoom.getTeam1Size()
                        + " | team2: " + currentRoom.getTeam2Size());
            }
        } else {
            sendMessage("MSG:still waiting...");
            if (isTeamCreator) {
                sendMessage("MSG:type START to begin, STATUS to see who's here, or LEAVE to exit");
            } else {
                sendMessage("MSG:type LEAVE if you want to leave the room");
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

    private void handleInGame(String line) {
        if (line.toUpperCase().startsWith("ANSWER:")) {
            String answer = line.substring(7).trim();
            if (answer.isEmpty()) {
                sendMessage("ERROR:Please provide an answer.");
                return;
            }
            if (currentRoom != null && !currentRoom.isGameOver()) {
                currentRoom.submitAnswer(currentUser.username, answer);
                sendMessage("MSG:got your answer: " + answer.toUpperCase());
            } else {
                sendMessage("MSG:Game is not active.");
            }
        } else {
            sendMessage("MSG:game is going on, answer with A, B, C or D");
        }
    }

    public void returnToMenu() {
        state = STATE_MENU;
        menuSub = MSUB_NONE;
        currentRoom = null;
        isTeamCreator = false;
        sendMessage("MSG:Game over! Returning to main menu...");
        showMenu();
    }

    private void showScores() {
        if (currentUser == null)
            return;
        List<ScoreEntry> entries = scoreManager.getScores(currentUser.username);
        if (entries.isEmpty()) {
            sendMessage("MSG:no scores yet, play a game first!");
        } else {
            String json = gson.toJson(entries);
            sendMessage("SCORES:" + json);
        }
        showMenu();
    }

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
        }
    }
}
