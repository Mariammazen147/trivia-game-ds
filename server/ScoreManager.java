package server;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class ScoreManager {
    private final String filePath;
    private final Map<String, List<ScoreEntry>> scores = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ScoreManager(String filePath) throws IOException {
        this.filePath = filePath;
        loadScores();
    }

    private void loadScores() throws IOException {
        File f = new File(filePath);
        if (!f.exists() || f.length() == 0) {
            return;
        }
        try (Reader r = new FileReader(f)) {
            Type mapType = new TypeToken<Map<String, List<ScoreEntry>>>() {}.getType();
            Map<String, List<ScoreEntry>> loaded = gson.fromJson(r, mapType);
            if (loaded != null) {
                scores.putAll(loaded);
            }
        }
    }

    public synchronized void addScore(String username, ScoreEntry entry) {
        scores.computeIfAbsent(username, k -> new ArrayList<>()).add(entry);
        try {
            saveScores();
        } catch (IOException e) {
            System.err.println("Failed to save scores: " + e.getMessage());
        }
    }

    public synchronized List<ScoreEntry> getScores(String username) {
        return scores.getOrDefault(username, Collections.emptyList());
    }

    public synchronized void saveScores() throws IOException {
        try (Writer w = new FileWriter(filePath)) {
            gson.toJson(scores, w);
        }
    }
}
