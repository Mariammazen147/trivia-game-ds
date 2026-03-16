package server;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

public class QuestionManager {
    private final List<Question> allQuestions = new ArrayList<>();
    private final Gson gson = new Gson();

    public QuestionManager(String filePath) throws IOException {
        loadQuestions(filePath);
    }

    private void loadQuestions(String filePath) throws IOException {
        try (Reader r = new FileReader(filePath)) {
            Type listType = new TypeToken<List<Question>>() {}.getType();
            List<Question> list = gson.fromJson(r, listType);
            if (list != null) {
                allQuestions.addAll(list);
            }
        }
    }

    /**
     * Returns a shuffled random selection of up to count questions from all questions.
     */
    public List<Question> getRandomQuestions(int count) {
        List<Question> copy = new ArrayList<>(allQuestions);
        Collections.shuffle(copy);
        return copy.subList(0, Math.min(count, copy.size()));
    }

    /**
     * Returns a filtered, shuffled selection. Pass null or empty string to skip that filter.
     */
    public List<Question> getQuestions(String category, String difficulty, int count) {
        List<Question> filtered = new ArrayList<>();
        for (Question q : allQuestions) {
            boolean categoryMatch = (category == null || category.trim().isEmpty()
                    || category.equalsIgnoreCase("any")
                    || q.category.equalsIgnoreCase(category));
            boolean difficultyMatch = (difficulty == null || difficulty.trim().isEmpty()
                    || difficulty.equalsIgnoreCase("any")
                    || q.difficulty.equalsIgnoreCase(difficulty));
            if (categoryMatch && difficultyMatch) {
                filtered.add(q);
            }
        }
        Collections.shuffle(filtered);
        return filtered.subList(0, Math.min(count, filtered.size()));
    }

    public List<String> getAvailableCategories() {
        Set<String> cats = new LinkedHashSet<>();
        for (Question q : allQuestions) {
            cats.add(q.category);
        }
        return new ArrayList<>(cats);
    }

    public List<String> getAvailableDifficulties() {
        return Arrays.asList("easy", "medium", "hard");
    }

    public int getTotalQuestions() {
        return allQuestions.size();
    }
}
