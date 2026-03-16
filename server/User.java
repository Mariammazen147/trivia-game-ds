package server;

public class User {
    public String name;
    public String username;
    public String passwordHash;

    public User() {}

    public User(String name, String username, String passwordHash) {
        this.name = name;
        this.username = username;
        this.passwordHash = passwordHash;
    }
}
