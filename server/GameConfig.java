package server;

import com.google.gson.*;
import java.io.*;
import java.util.List;

public class GameConfig {
    public int serverPort;
    public int minPlayersPerTeam;
    public int maxPlayersPerTeam;
    public int questionTimeSeconds;
    public List<Integer> timeWarnings;

    public static GameConfig load(String path) throws IOException {
        try (Reader r = new FileReader(path)) {
            return new Gson().fromJson(r, GameConfig.class);
        }
    }
}
