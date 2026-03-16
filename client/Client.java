package client;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Simple interactive client for the Trivia Game server.
 *
 * Usage:
 *   java -cp out client.Client [host] [port]
 *
 * Defaults: host=localhost, port=5000
 */
public class Client {

    // Minimal JSON helpers — avoids requiring Gson on the client classpath
    // We only need to parse a handful of known keys for display purposes.

    public static void main(String[] args) {
        String host = "localhost";
        int    port = 5000;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port '" + args[1] + "', using default 5000.");
            }
        }

        System.out.println("Connecting to " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected! Type your commands below.\n");

            PrintWriter out = new PrintWriter(
                    new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            BufferedReader serverIn = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            BufferedReader userIn = new BufferedReader(new InputStreamReader(System.in));

            // Thread that reads from the server and prints formatted output
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

            // Main thread: read user input and send to server
            String userLine;
            while ((userLine = userIn.readLine()) != null) {
                // Allow typing just the letter (e.g. "A") instead of "ANSWER:A"
                if (userLine.matches("[A-Da-d]")) {
                    userLine = "ANSWER:" + userLine.toUpperCase();
                }
                out.println(userLine);
                if (!readerThread.isAlive()) break;
            }

        } catch (ConnectException e) {
            System.err.println("Could not connect to " + host + ":" + port
                    + ". Is the server running?");
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }

        System.out.println("Client exited.");
    }

    // -------------------------------------------------------------------------
    // Display logic — parses server message prefixes for nice output
    // -------------------------------------------------------------------------

    private static void displayServerMessage(String raw) {
        if (raw == null || raw.isEmpty()) return;

        int colon = raw.indexOf(':');
        if (colon < 0) {
            System.out.println(raw);
            return;
        }

        String prefix  = raw.substring(0, colon).toUpperCase();
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
                // Unknown prefix — print as-is
                System.out.println(raw);
                break;
        }
    }

    // -------------------------------------------------------------------------
    // QUESTION display
    // -------------------------------------------------------------------------
    private static void displayQuestion(String json) {
        System.out.println();
        System.out.println("==========================================");

        String number     = extractString(json, "number");
        String total      = extractString(json, "total");
        String text       = extractString(json, "text");
        String category   = extractString(json, "category");
        String difficulty = extractString(json, "difficulty");

        if (number != null && total != null) {
            System.out.println("  Question " + number + " of " + total
                    + "  [" + category + " | " + difficulty + "]");
        }
        System.out.println("------------------------------------------");
        System.out.println("  " + text);
        System.out.println();

        // Parse the choices array
        List<String> choices = extractStringArray(json, "choices");
        for (String choice : choices) {
            System.out.println("    " + choice);
        }
        System.out.println("==========================================");
        System.out.print("Your answer (A/B/C/D): ");
    }

    // -------------------------------------------------------------------------
    // RESULT display
    // -------------------------------------------------------------------------
    private static void displayResult(String json) {
        System.out.println();
        System.out.println("---------- RESULT ----------");

        String correct      = extractString(json, "correctAnswer");
        String questionText = extractString(json, "questionText");

        if (questionText != null) {
            System.out.println("Q: " + questionText);
        }
        System.out.println("Correct answer: " + correct);

        // playerResults array
        List<Map<String, String>> playerResults = extractObjectArray(json, "playerResults");
        if (!playerResults.isEmpty()) {
            System.out.println();
            System.out.printf("  %-15s %-10s %-8s %-12s %-10s%n",
                    "Player", "Answer", "Correct", "Points", "Total");
            System.out.println("  " + "-".repeat(57));
            for (Map<String, String> pr : playerResults) {
                String username    = pr.getOrDefault("username", "?");
                String answer      = pr.getOrDefault("answer", "?");
                String isCorrect   = pr.getOrDefault("correct", "false");
                String pts         = pr.getOrDefault("pointsEarned", "0");
                String total       = pr.getOrDefault("totalScore", "0");
                String tick        = "true".equalsIgnoreCase(isCorrect) ? "YES" : "NO";
                System.out.printf("  %-15s %-10s %-8s %-12s %-10s%n",
                        username, answer, tick, pts, total);
            }
        }

        // Team scores if present
        String team1Score = extractString(json, "team1Score");
        String team2Score = extractString(json, "team2Score");
        if (team1Score != null) {
            System.out.println();
            System.out.println("  Team 1 score: " + team1Score
                    + "  |  Team 2 score: " + team2Score);
        }
        System.out.println("----------------------------");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // SCORES display
    // -------------------------------------------------------------------------
    private static void displayScores(String json) {
        System.out.println();
        System.out.println("========== YOUR SCORES ==========");

        if (json.trim().equals("[]") || json.trim().isEmpty()) {
            System.out.println("  No scores recorded yet.");
            System.out.println("=================================");
            return;
        }

        List<Map<String, String>> entries = extractObjectArray(json, null);
        if (entries.isEmpty()) {
            System.out.println("  No scores recorded yet.");
        } else {
            System.out.printf("  %-12s %-8s %-8s %-8s %-8s%n",
                    "Date", "Mode", "Score", "Total Q", "Correct");
            System.out.println("  " + "-".repeat(48));
            for (Map<String, String> e : entries) {
                System.out.printf("  %-12s %-8s %-8s %-8s %-8s%n",
                        e.getOrDefault("date", "?"),
                        e.getOrDefault("mode", "?"),
                        e.getOrDefault("score", "?"),
                        e.getOrDefault("totalQuestions", "?"),
                        e.getOrDefault("correctAnswers", "?"));
            }
        }
        System.out.println("=================================");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // GAMEOVER display
    // -------------------------------------------------------------------------
    private static void displayGameOver(String json) {
        System.out.println();
        System.out.println("***** GAME OVER *****");

        String winner = extractString(json, "winner");
        if (winner != null) {
            System.out.println("Winner: " + winner);
        }

        String team1Score = extractString(json, "team1Score");
        String team2Score = extractString(json, "team2Score");
        if (team1Score != null) {
            System.out.println("Team 1 Final Score: " + team1Score);
            System.out.println("Team 2 Final Score: " + team2Score);
        }

        List<Map<String, String>> scores = extractObjectArray(json, "scores");
        if (!scores.isEmpty()) {
            System.out.println();
            System.out.println("  Final Leaderboard:");
            System.out.printf("  %-5s %-15s %-10s%n", "Rank", "Player", "Score");
            System.out.println("  " + "-".repeat(32));
            int rank = 1;
            for (Map<String, String> s : scores) {
                System.out.printf("  %-5d %-15s %-10s%n",
                        rank++,
                        s.getOrDefault("username", "?"),
                        s.getOrDefault("score", "?"));
            }
        }
        System.out.println("*********************");
        System.out.println();
    }

    // =========================================================================
    // Minimal JSON parsing helpers
    // These cover the specific structures returned by the server without
    // requiring a full JSON library on the client.
    // =========================================================================

    /**
     * Extract the value of a simple string or number field from a flat JSON object.
     * Handles both quoted and unquoted values.
     */
    static String extractString(String json, String key) {
        if (json == null || key == null) return null;

        // Try quoted value: "key":"value" or "key": "value"
        String quotedPattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(quotedPattern).matcher(json);
        if (m.find()) return m.group(1);

        // Try unquoted value (numbers, booleans): "key":value or "key": value
        String unquotedPattern = "\"" + key + "\"\\s*:\\s*([^,}\\]\\s]+)";
        m = java.util.regex.Pattern.compile(unquotedPattern).matcher(json);
        if (m.find()) return m.group(1).trim();

        return null;
    }

    /**
     * Extract a JSON array of strings: "key":["a","b","c"]
     */
    static List<String> extractStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String arrayJson = extractRawArray(json, key);
        if (arrayJson == null) return result;

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"([^\"]*)\"").matcher(arrayJson);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }

    /**
     * Extract an array of flat objects.
     * If key is null, treats the entire json as an array.
     */
    static List<Map<String, String>> extractObjectArray(String json, String key) {
        List<Map<String, String>> result = new ArrayList<>();
        String arrayJson = (key == null) ? json : extractRawArray(json, key);
        if (arrayJson == null) return result;

        // Split on individual objects: find each {...}
        java.util.regex.Matcher objMatcher = java.util.regex.Pattern
                .compile("\\{([^{}]*)\\}").matcher(arrayJson);
        while (objMatcher.find()) {
            String objBody = objMatcher.group(1);
            Map<String, String> map = new LinkedHashMap<>();

            // Extract all key:value pairs from this object
            java.util.regex.Matcher kvMatcher = java.util.regex.Pattern
                    .compile("\"([^\"]+)\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\]\"]+))").matcher(objBody);
            while (kvMatcher.find()) {
                String k = kvMatcher.group(1);
                String v = kvMatcher.group(2) != null
                        ? kvMatcher.group(2)
                        : kvMatcher.group(3).trim();
                map.put(k, v);
            }
            if (!map.isEmpty()) result.add(map);
        }
        return result;
    }

    /**
     * Find the raw JSON array string for a given key, e.g. "choices":[...]
     */
    private static String extractRawArray(String json, String key) {
        String search = "\"" + key + "\"\\s*:\\s*\\[";
        java.util.regex.Matcher start = java.util.regex.Pattern
                .compile(search).matcher(json);
        if (!start.find()) return null;

        int bracketStart = start.end() - 1; // position of '['
        int depth = 0;
        for (int i = bracketStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(bracketStart, i + 1);
                }
            }
        }
        return null;
    }
}
