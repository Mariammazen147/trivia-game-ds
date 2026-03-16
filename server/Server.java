package server;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Server {

    public static void main(String[] args) {
        String configPath    = "data/config.json";
        String usersPath     = "data/users.json";
        String questionsPath = "data/questions.json";
        String scoresPath    = "data/scores.json";

        // Allow overriding data directory via first argument
        if (args.length > 0) {
            String dataDir = args[0];
            configPath    = dataDir + "/config.json";
            usersPath     = dataDir + "/users.json";
            questionsPath = dataDir + "/questions.json";
            scoresPath    = dataDir + "/scores.json";
        }

        GameConfig config;
        UserManager userManager;
        QuestionManager questionManager;
        ScoreManager scoreManager;

        try {
            System.out.println("[Server] Loading configuration from " + configPath);
            config = GameConfig.load(configPath);
        } catch (IOException e) {
            System.err.println("[Server] Failed to load config: " + e.getMessage());
            System.err.println("[Server] Make sure " + configPath + " exists.");
            return;
        }

        try {
            System.out.println("[Server] Loading users from " + usersPath);
            userManager = new UserManager(usersPath);
        } catch (IOException e) {
            System.err.println("[Server] Failed to load users: " + e.getMessage());
            return;
        }

        try {
            System.out.println("[Server] Loading questions from " + questionsPath);
            questionManager = new QuestionManager(questionsPath);
            System.out.println("[Server] Loaded " + questionManager.getTotalQuestions() + " questions.");
        } catch (IOException e) {
            System.err.println("[Server] Failed to load questions: " + e.getMessage());
            return;
        }

        try {
            System.out.println("[Server] Loading scores from " + scoresPath);
            scoreManager = new ScoreManager(scoresPath);
        } catch (IOException e) {
            System.err.println("[Server] Failed to load scores: " + e.getMessage());
            return;
        }

        ConcurrentHashMap<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
        ExecutorService threadPool = Executors.newCachedThreadPool();

        int port = config.serverPort;
        System.out.println("[Server] Starting on port " + port + "...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[Server] Listening for connections on port " + port);
            System.out.println("[Server] Press Ctrl+C to stop.");

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientAddr = clientSocket.getInetAddress().getHostAddress()
                            + ":" + clientSocket.getPort();
                    System.out.println("[Server] New connection from " + clientAddr);

                    ClientHandler handler = new ClientHandler(
                            clientSocket,
                            userManager,
                            questionManager,
                            scoreManager,
                            gameRooms,
                            config
                    );
                    threadPool.execute(handler);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        System.err.println("[Server] Error accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[Server] Could not start server on port " + port + ": " + e.getMessage());
        } finally {
            threadPool.shutdown();
            System.out.println("[Server] Server stopped.");
        }
    }
}
