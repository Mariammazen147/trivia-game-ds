package server;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class UserManager {
    private final String filePath;
    private final Map<String, User> users = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public UserManager(String filePath) throws IOException {
        this.filePath = filePath;
        loadUsers();
    }

    private void loadUsers() throws IOException {
        File f = new File(filePath);
        if (!f.exists()) {
            return;
        }
        try (Reader r = new FileReader(f)) {
            Type listType = new TypeToken<List<User>>() {}.getType();
            List<User> list = gson.fromJson(r, listType);
            if (list != null) {
                for (User u : list) {
                    users.put(u.username, u);
                }
            }
        }
    }

    public synchronized void saveUsers() throws IOException {
        List<User> list = new ArrayList<>(users.values());
        try (Writer w = new FileWriter(filePath)) {
            gson.toJson(list, w);
        }
    }

    /**
     * Attempt to log in. Returns the User on success, null on failure.
     */
    public synchronized User login(String username, String password) {
        User u = users.get(username);
        if (u == null) return null;
        String hashed = sha256(password);
        if (hashed.equals(u.passwordHash)) {
            return u;
        }
        return null;
    }

    /**
     * Register a new user. Returns null on success, or an error message string on failure.
     */
    public synchronized String register(String name, String username, String password) {
        if (users.containsKey(username)) {
            return "Username already taken.";
        }
        if (username == null || username.trim().isEmpty()) {
            return "Username cannot be empty.";
        }
        if (password == null || password.trim().isEmpty()) {
            return "Password cannot be empty.";
        }
        String hashed = sha256(password);
        User u = new User(name, username, hashed);
        users.put(username, u);
        try {
            saveUsers();
        } catch (IOException e) {
            return "Failed to save user data: " + e.getMessage();
        }
        return null; // success
    }

    public synchronized User getUser(String username) {
        return users.get(username);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
