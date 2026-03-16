package server;

import com.google.gson.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

public class GameRoom {
    private final String roomId;
    private final String roomName;
    private final boolean isTeamMode;
    private final String category;
    private final String difficulty;
    private final int numQuestions;

    private final List<ClientHandler> team1 = new ArrayList<>();
    private final List<ClientHandler> team2 = new ArrayList<>();

    private final QuestionManager questionManager;
    private final GameConfig config;
    private final ScoreManager scoreManager;

    private List<Question> questions;
    private int currentQuestionIndex = 0;
    private Question currentQuestion;

    // username -> answer letter
    private final Map<String, String> answers = new HashMap<>();
    // username -> timestamp when answered (ms)
    private final Map<String, Long> answerTimestamps = new HashMap<>();
    // username -> total score
    private final Map<String, Integer> playerScores = new HashMap<>();

    private boolean gameStarted = false;
    private volatile boolean timerActive = false;
    private volatile boolean gameOver = false;

    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;

    public GameRoom(String roomId, String roomName, boolean isTeamMode,
                    String category, String difficulty, int numQuestions,
                    QuestionManager questionManager, GameConfig config,
                    ScoreManager scoreManager) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.isTeamMode = isTeamMode;
        this.category = category;
        this.difficulty = difficulty;
        this.numQuestions = numQuestions;
        this.questionManager = questionManager;
        this.config = config;
        this.scoreManager = scoreManager;
    }

    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public boolean isTeamMode() { return isTeamMode; }
    public boolean isGameStarted() { return gameStarted; }
    public boolean isGameOver() { return gameOver; }

    public synchronized boolean addPlayer(ClientHandler handler, int team) {
        if (gameStarted) return false;
        List<ClientHandler> targetTeam = (team == 1) ? team1 : team2;
        if (targetTeam.size() >= config.maxPlayersPerTeam) return false;
        targetTeam.add(handler);
        playerScores.put(handler.getUsername(), 0);
        return true;
    }

    public synchronized void removePlayer(ClientHandler handler) {
        team1.remove(handler);
        team2.remove(handler);
        playerScores.remove(handler.getUsername());
    }

    public synchronized int getTeam1Size() { return team1.size(); }
    public synchronized int getTeam2Size() { return team2.size(); }

    public synchronized List<ClientHandler> getAllPlayers() {
        List<ClientHandler> all = new ArrayList<>();
        all.addAll(team1);
        all.addAll(team2);
        return all;
    }

    public synchronized void startGame() {
        if (gameStarted) return;
        gameStarted = true;
        questions = questionManager.getQuestions(category, difficulty, numQuestions);
        if (questions.isEmpty()) {
            broadcast("MSG: No questions found for the selected category/difficulty. Game cancelled.");
            gameOver = true;
            return;
        }
        broadcast("MSG: Game is starting! Get ready...");
        currentQuestionIndex = 0;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        askNextQuestion();
    }

    private synchronized void askNextQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            endGame();
            return;
        }
        answers.clear();
        answerTimestamps.clear();
        currentQuestion = questions.get(currentQuestionIndex);
        currentQuestionIndex++;

        broadcastQuestion();
        startTimer();
    }

    private synchronized void broadcastQuestion() {
        if (currentQuestion == null) return;
        Map<String, Object> qData = new LinkedHashMap<>();
        qData.put("id", currentQuestion.id);
        qData.put("number", currentQuestionIndex);
        qData.put("total", questions.size());
        qData.put("text", currentQuestion.text);
        qData.put("category", currentQuestion.category);
        qData.put("difficulty", currentQuestion.difficulty);
        qData.put("choices", currentQuestion.choices);
        String json = gson.toJson(qData);
        broadcast("QUESTION:" + json);
    }

    private void startTimer() {
        timerActive = true;
        final int totalTime = config.questionTimeSeconds;
        // Sort warnings descending so we schedule from the end of the countdown
        List<Integer> warnings = config.timeWarnings != null
                ? new ArrayList<>(config.timeWarnings) : new ArrayList<>();
        Collections.sort(warnings, Collections.reverseOrder());

        // Schedule warnings
        for (int w : warnings) {
            if (w < totalTime) {
                long delay = totalTime - w;
                scheduler.schedule(() -> {
                    if (timerActive) {
                        broadcast("TIMER:" + w);
                    }
                }, delay, TimeUnit.SECONDS);
            }
        }

        // Schedule expiry
        scheduler.schedule(() -> {
            if (timerActive) {
                timerActive = false;
                evaluateAnswers();
            }
        }, totalTime, TimeUnit.SECONDS);
    }

    public synchronized void submitAnswer(String username, String answer) {
        if (!gameStarted || gameOver || currentQuestion == null) return;
        if (answers.containsKey(username)) return; // already answered
        answers.put(username, answer.toUpperCase().trim());
        answerTimestamps.put(username, System.currentTimeMillis());

        // Check if all players have answered
        List<ClientHandler> all = getAllPlayers();
        if (answers.size() >= all.size()) {
            timerActive = false;
            evaluateAnswers();
        }
    }

    private synchronized void evaluateAnswers() {
        if (currentQuestion == null) return;
        String correctAnswer = currentQuestion.answer.toUpperCase().trim();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", currentQuestion.id);
        result.put("correctAnswer", correctAnswer);
        result.put("questionText", currentQuestion.text);

        List<Map<String, Object>> playerResults = new ArrayList<>();
        List<ClientHandler> all = getAllPlayers();

        // Find fastest correct answerer for speed bonus
        long fastestTime = Long.MAX_VALUE;
        for (ClientHandler p : all) {
            String uname = p.getUsername();
            String given = answers.getOrDefault(uname, "");
            if (given.equals(correctAnswer)) {
                long ts = answerTimestamps.getOrDefault(uname, Long.MAX_VALUE);
                if (ts < fastestTime) fastestTime = ts;
            }
        }

        for (ClientHandler p : all) {
            String uname = p.getUsername();
            String given = answers.getOrDefault(uname, "(no answer)");
            boolean correct = given.equals(correctAnswer);
            int points = 0;

            if (correct) {
                points = 100;
                // Speed bonus: first correct answerer gets +50
                long ts = answerTimestamps.getOrDefault(uname, Long.MAX_VALUE);
                if (ts == fastestTime && fastestTime != Long.MAX_VALUE) {
                    points += 50;
                }
            }

            int prev = playerScores.getOrDefault(uname, 0);
            playerScores.put(uname, prev + points);

            Map<String, Object> pr = new LinkedHashMap<>();
            pr.put("username", uname);
            pr.put("answer", given);
            pr.put("correct", correct);
            pr.put("pointsEarned", points);
            pr.put("totalScore", playerScores.get(uname));
            playerResults.add(pr);
        }
        result.put("playerResults", playerResults);

        // Team scores if applicable
        if (isTeamMode) {
            int team1Score = 0;
            int team2Score = 0;
            for (ClientHandler p : team1) {
                team1Score += playerScores.getOrDefault(p.getUsername(), 0);
            }
            for (ClientHandler p : team2) {
                team2Score += playerScores.getOrDefault(p.getUsername(), 0);
            }
            result.put("team1Score", team1Score);
            result.put("team2Score", team2Score);
        }

        String json = gson.toJson(result);
        broadcast("RESULT:" + json);

        // Pause briefly, then ask next question
        scheduler.schedule(() -> {
            synchronized (GameRoom.this) {
                askNextQuestion();
            }
        }, 3, TimeUnit.SECONDS);
    }

    private synchronized void endGame() {
        gameOver = true;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        Map<String, Object> gameOverData = new LinkedHashMap<>();
        gameOverData.put("event", "GAMEOVER");

        List<Map<String, Object>> finalScores = new ArrayList<>();
        List<ClientHandler> all = getAllPlayers();

        // Sort by score descending
        all.sort((a, b) -> playerScores.getOrDefault(b.getUsername(), 0)
                - playerScores.getOrDefault(a.getUsername(), 0));

        for (ClientHandler p : all) {
            String uname = p.getUsername();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("username", uname);
            entry.put("score", playerScores.getOrDefault(uname, 0));
            finalScores.add(entry);
        }
        gameOverData.put("scores", finalScores);

        if (isTeamMode) {
            int team1Score = 0;
            int team2Score = 0;
            for (ClientHandler p : team1) {
                team1Score += playerScores.getOrDefault(p.getUsername(), 0);
            }
            for (ClientHandler p : team2) {
                team2Score += playerScores.getOrDefault(p.getUsername(), 0);
            }
            gameOverData.put("team1Score", team1Score);
            gameOverData.put("team2Score", team2Score);
            String winner;
            if (team1Score > team2Score) winner = "Team 1";
            else if (team2Score > team1Score) winner = "Team 2";
            else winner = "Tie";
            gameOverData.put("winner", winner);
        } else {
            // Single player
            if (!all.isEmpty()) {
                String uname = all.get(0).getUsername();
                gameOverData.put("winner", uname);
            }
        }

        String json = gson.toJson(gameOverData);
        broadcast("GAMEOVER:" + json);

        // Save scores for each player
        String today = LocalDate.now().toString();
        String mode = isTeamMode ? "team" : "solo";
        for (ClientHandler p : all) {
            String uname = p.getUsername();
            int score = playerScores.getOrDefault(uname, 0);
            // Count correct: score / 100 (ignoring speed bonuses for simplicity, approximate)
            int correctApprox = score / 100;
            ScoreEntry entry = new ScoreEntry(today, mode, score, questions.size(), correctApprox);
            scoreManager.addScore(uname, entry);
        }

        // After a delay, notify players and clean up
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            for (ClientHandler p : getAllPlayers()) {
                p.returnToMenu();
            }
        }).start();
    }

    public synchronized void broadcast(String msg) {
        List<ClientHandler> all = getAllPlayers();
        for (ClientHandler p : all) {
            p.sendMessage(msg);
        }
    }

    public String getRoomInfo() {
        return String.format("%s | Category: %s | Difficulty: %s | Questions: %d | Team1: %d | Team2: %d",
                roomName, category, difficulty, numQuestions, team1.size(), team2.size());
    }
}
