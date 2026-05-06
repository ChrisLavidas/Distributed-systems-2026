package shared;

import java.io.*;
import java.nio.file.*;


public class JsonGameParser {

    public static Game parseFile(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        return parseString(content);
    }

    public static Game parseString(String json) {
        String gameName  = extractString(json, "GameName");
        String provider  = extractString(json, "ProviderName");
        double stars     = extractDouble(json, "Stars");
        int    noOfVotes = (int) extractDouble(json, "NoOfVotes");
        String gameLogo  = extractString(json, "GameLogo");
        double minBet    = extractDouble(json, "MinBet");
        double maxBet    = extractDouble(json, "MaxBet");
        String riskLevel = extractString(json, "RiskLevel");
        String hashKey   = extractString(json, "HashKey");

        return new Game(gameName, provider, stars, noOfVotes,
                        gameLogo, minBet, maxBet, riskLevel, hashKey);
    }

    private static String extractString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return "";
        int colon = json.indexOf(':', idx);
        int q1    = json.indexOf('"', colon + 1);
        int q2    = json.indexOf('"', q1 + 1);
        return json.substring(q1 + 1, q2);
    }

    private static double extractDouble(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx < 0) return 0.0;
        int pos = json.indexOf(':', idx) + 1;
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        int end = pos;
        while (end < json.length() &&
               (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-'))
            end++;
        try { return Double.parseDouble(json.substring(pos, end)); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
