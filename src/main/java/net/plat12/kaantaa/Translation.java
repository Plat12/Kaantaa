package net.plat12.kaantaa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.plat12.kaantaa.Util.*;

public class Translation {

    public static final Type LOAD_TYPE = new TypeToken<Map<String, Entry>>() {
    }.getType();
    public static final Type CREATE_TYPE = new TypeToken<LinkedHashMap<String, String>>() {
    }.getType();
    private static final String SAVE_FILE_SUFFIX = "_kaantaa.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Entry> values;

    private Translation(Map<String, Entry> values) {
        this.values = values;
    }

    private static Comparator<Map.Entry<String, Entry>> getEntryComparator(SortingType sortingType, boolean reversed) {
        Comparator<Map.Entry<String, Entry>> comparator = (e1, e2) ->
                switch (sortingType) {
                    case APPEARANCE -> Integer.compare(e1.getValue().filePos, e2.getValue().filePos);
                    case KEY -> e1.getKey().compareTo(e2.getKey());
                    case ORIGINAL -> e1.getValue().original.compareTo(e2.getValue().original);
                    case TRANSLATION -> e1.getValue().translated.compareTo(e2.getValue().translated);
                };
        return reversed ? comparator.reversed() : comparator;
    }

    public static Translation load(String filePath) {
        if (!filePath.toLowerCase().endsWith(SAVE_FILE_SUFFIX)) return null;
        try {
            String json = Files.readString(Path.of(filePath));
            Map<String, Entry> values = GSON.fromJson(json, LOAD_TYPE);
            if (values == null || values.isEmpty()) return null;

            int pos = 0;
            for (Entry entry : values.values()) {
                entry.filePos = pos++;
            }

            return new Translation(values);
        } catch (IOException | JsonSyntaxException e) {
            return null;
        }
    }

    public static Translation create(String json) throws IOException {
        String content = Files.readString(Path.of(json));
        LinkedHashMap<String, String> rawEntries = GSON.fromJson(content, CREATE_TYPE);

        Map<String, Entry> values = new LinkedHashMap<>();
        int cursor = 0;
        for (Map.Entry<String, String> entry : rawEntries.entrySet()) {
            values.put(entry.getKey(), new Entry(entry.getValue(), cursor++));
        }
        return new Translation(values);
    }

    public void replaceInAll(String target, String replacement, boolean matchCase) {
        if (target.isEmpty()) return;
        values.values().forEach(e -> e.replaceInTranslated(target, replacement, matchCase));
    }

    public Collection<Entry> allEntries() {
        return values.values();
    }

    public List<Map.Entry<String, Entry>> getMatching(SortingType sortingType, boolean reversed, SearchLocation searchLocation,
                                                      String query, FilterType filterType) {
        String normalizedQuery = normalize(query);
        return values.entrySet().stream().filter(e -> switch (filterType) {
                    case ANY -> true;
                    case UNFINISHED -> !e.getValue().finished;
                    case FINISHED -> e.getValue().finished;
                    case HAS_PLACEHOLDERS -> e.getValue().originalPlaceholderCount() > 0;
                } && switch (searchLocation) {
                    case EVERYWHERE ->
                            matchesQuery(e.getKey(), normalizedQuery) || e.getValue().matches(searchLocation, normalizedQuery);
                    case KEY -> matchesQuery(e.getKey(), normalizedQuery);
                    case ORIGINAL, TRANSLATION -> e.getValue().matches(searchLocation, normalizedQuery);
                })
                .sorted(getEntryComparator(sortingType, reversed)).toList();
    }

    public void save(String directory, String langCode) throws IOException {
        Path outputFile = Path.of(directory, langCode + SAVE_FILE_SUFFIX);

        Map<String, Entry> orderedValues = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().filePos))
                .forEachOrdered(e -> orderedValues.put(e.getKey(), e.getValue()));

        String json = GSON.toJson(orderedValues);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, json, StandardCharsets.UTF_8);
    }

    public void output(String directory, String langCode) throws IOException {
        Path outputFile = Path.of(directory, langCode + ".json");
        Map<String, String> outputMap = new TreeMap<>();
        for (Map.Entry<String, Entry> entry : values.entrySet()) {
            outputMap.put(entry.getKey(), entry.getValue().translated);
        }
        String json = GSON.toJson(outputMap);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, json, StandardCharsets.UTF_8);
    }

    public Translation mergeFrom(Translation newTranslation) {
        Map<String, Entry> newValues = new LinkedHashMap<>();
        int pos = 0;
        for (Map.Entry<String, Entry> newEntry : newTranslation.values.entrySet()) {
            String key = newEntry.getKey();
            String newOriginal = newEntry.getValue().original;
            Entry oldEntry = this.values.get(key);
            Entry updatedEntry = new Entry(newOriginal, pos);
            if (oldEntry != null) {
                updatedEntry.setTranslated(oldEntry.translated);
                updatedEntry.markAsFinished(oldEntry.finished && oldEntry.original.equals(newOriginal));
            }
            newValues.put(key, updatedEntry);
            pos++;
        }
        return new Translation(newValues);
    }

    public MergeStats computeMergeStats(Translation newTranslation) {
        int added = 0, removed = 0, changed = 0;
        Set<String> oldKeys = this.values.keySet();
        Set<String> newKeys = newTranslation.values.keySet();

        for (String key : newKeys) {
            if (!oldKeys.contains(key)) {
                added++;
            } else {
                Entry oldEntry = this.values.get(key);
                Entry newEntry = newTranslation.values.get(key);
                if (!oldEntry.original.equals(newEntry.original)) {
                    changed++;
                }
            }
        }
        for (String key : oldKeys) {
            if (!newKeys.contains(key)) {
                removed++;
            }
        }
        return new MergeStats(added, removed, changed);
    }

    public int size() {
        return values.size();
    }

    public enum SortingType implements DisplayableEnum {
        KEY,
        APPEARANCE,
        ORIGINAL,
        TRANSLATION
    }

    public enum SearchLocation implements DisplayableEnum {
        EVERYWHERE,
        KEY,
        ORIGINAL,
        TRANSLATION
    }

    public enum FilterType implements DisplayableEnum {
        ANY,
        UNFINISHED,
        FINISHED,
        HAS_PLACEHOLDERS
    }

    public interface DisplayableEnum {
        String name();

        default String displayName() {
            return capitalize(name());
        }
    }

    public record MergeStats(int added, int removed, int changed) {
    }

    public static class Entry {
        private final String original;
        private transient int filePos;
        private String translated;
        private boolean finished = false;

        public Entry(String original, int filePos) {
            this.original = original;
            this.translated = original;
            this.filePos = filePos;
        }

        public String getTranslated() {
            return translated;
        }

        public void setTranslated(String translated) {
            this.translated = translated;
        }

        public String getOriginal() {
            return original;
        }

        public void markAsFinished(boolean finished) {
            this.finished = finished;
        }

        public boolean isFinished() {
            return finished;
        }

        public int originalPlaceholderCount() {
            return countPlaceholders(original);
        }

        public int translatedPlaceholderCount() {
            return countPlaceholders(translated);
        }

        public boolean hasPlaceholderMismatch() {
            return originalPlaceholderCount() != translatedPlaceholderCount();
        }


        public boolean matches(SearchLocation searchLocation, String query) {
            return switch (searchLocation) {
                case EVERYWHERE -> matchesQuery(original, query) || matchesQuery(translated, query);
                case KEY -> false;
                case ORIGINAL -> matchesQuery(original, query);
                case TRANSLATION -> matchesQuery(translated, query);
            };
        }

        public void replaceInTranslated(String target, String replacement, boolean matchCase) {
            if (target.isEmpty()) return;
            if (matchCase) {
                setTranslated(translated.replace(target, replacement));
            } else {
                String result = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(translated).replaceAll(Matcher.quoteReplacement(replacement));
                setTranslated(result);
            }
        }
    }
}