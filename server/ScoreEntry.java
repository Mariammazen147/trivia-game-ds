package server;

public class ScoreEntry {
    public String date;
    public String mode;
    public int score;
    public int totalQuestions;
    public int correctAnswers;

    public ScoreEntry() {}

    public ScoreEntry(String date, String mode, int score, int totalQuestions, int correctAnswers) {
        this.date = date;
        this.mode = mode;
        this.score = score;
        this.totalQuestions = totalQuestions;
        this.correctAnswers = correctAnswers;
    }
}
