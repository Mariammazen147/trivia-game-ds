package client;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;

public class Client {

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;

        if (args.length >= 1)
            host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port, using default 5000.");
            }
        }

        System.out.println("Connecting to " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected! Type your commands below.\n");

            PrintWriter out = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream())),
                    true);
            BufferedReader serverIn = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            BufferedReader userIn = new BufferedReader(
                    new InputStreamReader(System.in));

            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = serverIn.readLine()) != null) {
                        displayServerMessage(line);
                    }
                } catch (IOException e) {
                    System.out.println("\n[Disconnected from server]");
                }
            }, "server-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            String userLine;
            while ((userLine = userIn.readLine()) != null) {
                if (userLine.matches("[A-Da-d]")) {
                    userLine = "ANSWER:" + userLine.toUpperCase();
                }
                out.println(userLine);
                if (!readerThread.isAlive())
                    break;
            }

        } catch (ConnectException e) {
            System.err.println("Could not connect to " + host + ":" + port
                    + ". Is the server running?");
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }

        System.out.println("Client exited.");
    }

    private static void displayServerMessage(String raw) {
        if (raw == null || raw.isEmpty())
            return;

        int colon = raw.indexOf(':');
        if (colon < 0) {
            System.out.println(raw);
            return;
        }

        String prefix = raw.substring(0, colon).toUpperCase();
        String payload = raw.substring(colon + 1);

        switch (prefix) {
            case "MSG":
                System.out.println(payload);
                break;
            case "MENU":
                System.out.println(payload);
                break;
            case "ERROR":
                System.out.println("[!] " + payload);
                break;
            case "TIMER":
                System.out.println("  Time remaining: " + payload + " seconds");
                break;
            case "QUESTION":
                displayQuestion(payload);
                break;
            case "RESULT":
                displayResult(payload);
                break;
            case "SCORES":
                displayScores(payload);
                break;
            case "GAMEOVER":
                displayGameOver(payload);
                break;
            default:
                System.out.println(raw);
                break;
        }
    }

    private static void displayQuestion(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);

            System.out.println();
            System.out.println("==========================================");

            String number = obj.has("number") ? obj.get("number").getAsString() : "?";
            String total = obj.has("total") ? obj.get("total").getAsString() : "?";
            String text = obj.has("text") ? obj.get("text").getAsString() : "?";
            String category = obj.has("category") ? obj.get("category").getAsString() : "?";
            String difficulty = obj.has("difficulty") ? obj.get("difficulty").getAsString() : "?";

            System.out.println("  Question " + number + " of " + total
                    + "  [" + category + " | " + difficulty + "]");
            System.out.println("------------------------------------------");
            System.out.println("  " + text);
            System.out.println();

            if (obj.has("choices")) {
                JsonArray choices = obj.getAsJsonArray("choices");
                for (JsonElement choice : choices) {
                    System.out.println("    " + choice.getAsString());
                }
            }

            System.out.println("==========================================");
            System.out.print("Your answer (A/B/C/D): ");

        } catch (JsonSyntaxException e) {
            System.out.println(json);
        }
    }

    private static void displayResult(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);

            System.out.println();
            System.out.println("---------- RESULT ----------");

            if (obj.has("questionText")) {
                System.out.println("Q: " + obj.get("questionText").getAsString());
            }
            if (obj.has("correctAnswer")) {
                System.out.println("Correct answer: " + obj.get("correctAnswer").getAsString());
            }

            if (obj.has("playerResults")) {
                JsonArray results = obj.getAsJsonArray("playerResults");
                System.out.println();
                System.out.printf("  %-15s %-10s %-8s %-12s %-10s%n",
                        "Player", "Answer", "Correct", "Points", "Total");
                System.out.println("  " + "-".repeat(57));

                for (JsonElement element : results) {
                    JsonObject pr = element.getAsJsonObject();
                    String username = pr.has("username") ? pr.get("username").getAsString() : "?";
                    String answer = pr.has("answer") ? pr.get("answer").getAsString() : "?";
                    String correct = pr.has("correct") ? pr.get("correct").getAsString() : "false";
                    String points = pr.has("pointsEarned") ? pr.get("pointsEarned").getAsString() : "0";
                    String total = pr.has("totalScore") ? pr.get("totalScore").getAsString() : "0";
                    String tick = correct.equals("true") ? "YES" : "NO";

                    System.out.printf("  %-15s %-10s %-8s %-12s %-10s%n",
                            username, answer, tick, points, total);
                }
            }

            if (obj.has("team1Score")) {
                System.out.println();
                System.out.println("  Team 1 score: " + obj.get("team1Score").getAsString()
                        + "  |  Team 2 score: " + obj.get("team2Score").getAsString());
            }

            System.out.println("----------------------------");
            System.out.println();

        } catch (JsonSyntaxException e) {
            System.out.println(json);
        }
    }

    private static void displayScores(String json) {
        try {
            System.out.println();
            System.out.println("========== YOUR SCORES ==========");

            if (json.trim().equals("[]") || json.trim().isEmpty()) {
                System.out.println("  No scores recorded yet.");
                System.out.println("=================================");
                return;
            }

            Type listType = new TypeToken<List<Map<String, String>>>() {
            }.getType();
            List<Map<String, String>> entries = gson.fromJson(json, listType);

            if (entries == null || entries.isEmpty()) {
                System.out.println("  No scores recorded yet.");
            } else {
                System.out.printf("  %-12s %-8s %-8s %-10s %-10s%n",
                        "Date", "Mode", "Score", "Questions", "Correct");
                System.out.println("  " + "-".repeat(50));
                for (Map<String, String> e : entries) {
                    System.out.printf("  %-12s %-8s %-8s %-10s %-10s%n",
                            e.getOrDefault("date", "?"),
                            e.getOrDefault("mode", "?"),
                            e.getOrDefault("score", "?"),
                            e.getOrDefault("totalQuestions", "?"),
                            e.getOrDefault("correctAnswers", "?"));
                }
            }

            System.out.println("=================================");
            System.out.println();

        } catch (JsonSyntaxException e) {
            System.out.println(json);
        }
    }

    private static void displayGameOver(String json) {
        try {
            JsonObject obj = gson.fromJson(json, JsonObject.class);

            System.out.println();
            System.out.println("***** GAME OVER *****");

            if (obj.has("winner")) {
                System.out.println("Winner: " + obj.get("winner").getAsString());
            }

            if (obj.has("team1Score")) {
                System.out.println("Team 1 Final Score: " + obj.get("team1Score").getAsString());
                System.out.println("Team 2 Final Score: " + obj.get("team2Score").getAsString());
            }

            if (obj.has("scores")) {
                JsonArray scores = obj.getAsJsonArray("scores");
                System.out.println();
                System.out.println("  Final Leaderboard:");
                System.out.printf("  %-5s %-15s %-10s%n", "Rank", "Player", "Score");
                System.out.println("  " + "-".repeat(32));
                int rank = 1;
                for (JsonElement element : scores) {
                    JsonObject s = element.getAsJsonObject();
                    String username = s.has("username") ? s.get("username").getAsString() : "?";
                    String score = s.has("score") ? s.get("score").getAsString() : "0";
                    System.out.printf("  %-5d %-15s %-10s%n", rank++, username, score);
                }
            }

            System.out.println("*********************");
            System.out.println();

        } catch (JsonSyntaxException e) {
            System.out.println(json);
        }
    }
}