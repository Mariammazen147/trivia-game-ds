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

    private final Map<String, String> answers = new HashMap<>();
    private final Map<String, Long> answerTimestamps = new HashMap<>();
    private final Map<String, Integer> playerScores = new HashMap<>();
    private final Map<String, Integer> correctCounts = new HashMap<>();

    private boolean gameStarted = false;
    private boolean timerActive = false;
    private boolean gameOver = false;

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

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public boolean isTeamMode() {
        return isTeamMode;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public synchronized boolean addPlayer(ClientHandler handler, int team) {
        if (gameStarted)
            return false;
        List<ClientHandler> targetTeam = (team == 1) ? team1 : team2;
        if (targetTeam.size() >= config.maxPlayersPerTeam)
            return false;
        targetTeam.add(handler);
        playerScores.put(handler.getUsername(), 0);
        correctCounts.put(handler.getUsername(), 0);
        return true;
    }

    public synchronized void removePlayer(ClientHandler handler) {
        team1.remove(handler);
        team2.remove(handler);
        playerScores.remove(handler.getUsername());
        correctCounts.remove(handler.getUsername());
    }

    public synchronized int getTeam1Size() {
        return team1.size();
    }

    public synchronized int getTeam2Size() {
        return team2.size();
    }

    public synchronized List<ClientHandler> getAllPlayers() {
        List<ClientHandler> all = new ArrayList<>();
        all.addAll(team1);
        all.addAll(team2);
        return all;
    }

    public synchronized void startGame() {
        if (gameStarted)
            return;
        gameStarted = true;
        questions = questionManager.getQuestions(category, difficulty, numQuestions);
        if (questions.isEmpty()) {
            broadcast("MSG:no questions found for that, try different settings");
            gameOver = true;
            return;
        }
        broadcast("MSG:starting! get ready");
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
        if (currentQuestion == null)
            return;
        Map<String, Object> qData = new HashMap<>();
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

        List<Integer> warnings = new ArrayList<>();
        if (config.timeWarnings != null) {
            warnings.addAll(config.timeWarnings);
        }

        for (int i = 0; i < warnings.size(); i++) {
            final int w = warnings.get(i);
            if (w < totalTime) {
                long delay = totalTime - w;
                scheduler.schedule(() -> {
                    if (timerActive) {
                        broadcast("TIMER:" + w);
                    }
                }, delay, TimeUnit.SECONDS);
            }
        }

        scheduler.schedule(() -> {
            if (timerActive) {
                timerActive = false;
                evaluateAnswers();
            }
        }, totalTime, TimeUnit.SECONDS);
    }

    public synchronized void submitAnswer(String username, String answer) {
        if (!gameStarted || gameOver || currentQuestion == null)
            return;
        if (answers.containsKey(username))
            return;
        answers.put(username, answer.toUpperCase().trim());
        answerTimestamps.put(username, System.currentTimeMillis());

        List<ClientHandler> all = getAllPlayers();
        if (answers.size() >= all.size()) {
            timerActive = false;
            evaluateAnswers();
        }
    }

    private synchronized void evaluateAnswers() {
        if (currentQuestion == null)
            return;
        String correctAnswer = currentQuestion.answer.toUpperCase().trim();

        Map<String, Object> result = new HashMap<>();
        result.put("questionId", currentQuestion.id);
        result.put("correctAnswer", correctAnswer);
        result.put("questionText", currentQuestion.text);

        List<ClientHandler> all = getAllPlayers();

        String fastestPlayer = null;
        long fastestTime = -1;

        for (ClientHandler p : all){
            String uname = p.getUsername();
            String given = answers.getOrDefault(uname, "");
            if (given.equals(correctAnswer)) {
                long ts = answerTimestamps.getOrDefault(uname, Long.MAX_VALUE);
                if (fastestPlayer == null || ts < fastestTime) {
                    fastestPlayer = uname;
                    fastestTime = ts;
                }
            }
        }

        List<Map<String, Object>> playerResults = new ArrayList<>();

        for (ClientHandler p : all) {
            String uname = p.getUsername();
            String given = answers.getOrDefault(uname, "(no answer)");
            boolean correct = given.equals(correctAnswer);
            int points = 0;

            if (correct) {
                points = 100;
                correctCounts.put(uname, correctCounts.getOrDefault(uname, 0) + 1);

                if (isTeamMode && uname.equals(fastestPlayer)){
                    points += 50;
                }
            }

            int prev = playerScores.getOrDefault(uname, 0);
            playerScores.put(uname, prev + points);

            Map<String, Object> pr = new HashMap<>();
            pr.put("username", uname);
            pr.put("answer", given);
            pr.put("correct", correct);
            pr.put("pointsEarned", points);
            pr.put("totalScore", playerScores.get(uname));
            playerResults.add(pr);
        }

        result.put("playerResults", playerResults);

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

        Map<String, Object> gameOverData = new HashMap<>();
        gameOverData.put("event", "GAMEOVER");

        List<ClientHandler> all = getAllPlayers();

        List<ClientHandler> sorted = new ArrayList<>(all);
        for (int i = 0; i < sorted.size() - 1; i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                int scoreI = playerScores.getOrDefault(sorted.get(i).getUsername(), 0);
                int scoreJ = playerScores.getOrDefault(sorted.get(j).getUsername(), 0);
                if (scoreJ > scoreI) {
                    ClientHandler temp = sorted.get(i);
                    sorted.set(i, sorted.get(j));
                    sorted.set(j, temp);
                }
            }
        }

        List<Map<String, Object>> finalScores = new ArrayList<>();
        for (ClientHandler p : sorted) {
            String uname = p.getUsername();
            Map<String, Object> entry = new HashMap<>();
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
            if (team1Score > team2Score) {
                winner = "Team 1";
            } else if (team2Score > team1Score) {
                winner = "Team 2";
            } else {
                winner = "Tie";
            }
            gameOverData.put("winner", winner);
        } else {
            if (!sorted.isEmpty()) {
                gameOverData.put("winner", sorted.get(0).getUsername());
            }
        }

        String json = gson.toJson(gameOverData);
        broadcast("GAMEOVER:" + json);

        String today = LocalDate.now().toString();
        String mode = isTeamMode ? "team" : "solo";
        for (ClientHandler p : all) {
            String uname = p.getUsername();
            int score = playerScores.getOrDefault(uname, 0);
            int correct = correctCounts.getOrDefault(uname, 0);
            ScoreEntry entry = new ScoreEntry(today, mode, score, questions.size(), correct);
            scoreManager.addScore(uname, entry);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            for (ClientHandler p : getAllPlayers()) {
                p.returnToMenu();
            }
            scheduler.shutdown();
        }, 2, TimeUnit.SECONDS);
    }

    public synchronized void broadcast(String msg) {
        List<ClientHandler> all = getAllPlayers();
        for (ClientHandler p : all) {
            p.sendMessage(msg);
        }
    }

    public String getRoomInfo() {
        return roomName + " | " + category + " | " + difficulty
                + " | " + numQuestions + " questions"
                + " | team1: " + team1.size()
                + " | team2: " + team2.size();
    }
}