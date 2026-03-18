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


    public synchronized User login(String username, String password) {
    User u = users.get(username);
    if (u == null) {
        throw new LoginException("404");
    }
    String hashed = sha256(password);
    if (!hashed.equals(u.passwordHash)) {
        throw new LoginException("401");
    }
    return u;
}


    public synchronized String register(String name, String username, String password){
    if (username == null || username.trim().isEmpty()) {
        return "Username cannot be empty.";
    }
    if (password == null || password.trim().isEmpty()) {
        return "Password cannot be empty.";
    }
    for (String existing : users.keySet()) {
        if (existing.equalsIgnoreCase(username.trim())) {
            return "Username already taken.";
        }
    }
    String hashed = sha256(password);
    User u = new User(name, username.trim(), hashed);
    users.put(username.trim(), u);
    try {
        saveUsers();
    } catch (IOException e) {
        return "Could not save user: " + e.getMessage();
    }
    return null;
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

    public static class LoginException extends RuntimeException {//401, 404
        public LoginException(String code) {
            super(code);
        }
    }
}
