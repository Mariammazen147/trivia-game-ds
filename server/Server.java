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
            System.out.println("loading config from " + configPath);
            config = GameConfig.load(configPath);
        } catch (IOException e) {
            System.err.println("couldn't load config file: " + e.getMessage());
            return;
        }

        try {
            System.out.println("loading users...");
            userManager = new UserManager(usersPath);
        } catch (IOException e) {
            System.err.println("error loading users: " + e.getMessage());
            return;
        }

        try {
            questionManager = new QuestionManager(questionsPath);
            System.out.println("loaded " + questionManager.getTotalQuestions() + " questions");
        } catch (IOException e) {
            System.err.println("error loading questions: " + e.getMessage());
            return;
        }

        try {
            System.out.println("loading scores...");
            scoreManager = new ScoreManager(scoresPath);
        } catch (IOException e) {
            System.err.println("error loading scores: " + e.getMessage());
            return;
        }

        ConcurrentHashMap<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
        ExecutorService threadPool = Executors.newCachedThreadPool();

        int port = config.serverPort;
        System.out.println("starting on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("server is up, waiting for players to connect...");
            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("new player connected");

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
                        System.err.println("problem accepting connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("failed to start on port " + port + ": " + e.getMessage());
        } finally {
            threadPool.shutdown();
            System.out.println("server stopped");
        }
    }
}
