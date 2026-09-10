package net.plat12.kaantaa;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Util {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?s");

    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("_", " ").toLowerCase();
    }

    public static boolean matchesQuery(String string, String normalizedQuery) {
        return normalize(string).contains(normalizedQuery);
    }

    public static String normalize(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }


    public static int countPlaceholders(String string) {
        if (string == null || string.isBlank()) {
            return 0;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(string);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
