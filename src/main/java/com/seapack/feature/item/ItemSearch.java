package com.seapack.feature.item;

import com.seapack.util.TextUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class ItemSearch {
    private final List<SearchEntry> entries;

    public ItemSearch(
            List<CustomItemDefinition> items,
            Function<CustomItemDefinition, String> setGroupResolver
    ) {
        this.entries = items.stream()
                .map(item -> new SearchEntry(item, targets(item, setGroupResolver.apply(item))))
                .toList();
    }

    public List<CustomItemDefinition> search(String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return entries.stream().map(SearchEntry::item).toList();
        }

        int maximumScore = Math.max(4, normalizedQuery.length() / 2 + 2);
        return entries.stream()
                .map(entry -> new SearchMatch(entry.item(), entry.score(normalizedQuery)))
                .filter(match -> match.score() <= maximumScore)
                .sorted(Comparator.comparingInt(SearchMatch::score)
                        .thenComparing(match -> match.item().id(), String.CASE_INSENSITIVE_ORDER))
                .map(SearchMatch::item)
                .toList();
    }

    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static int manhattanDistance(String query, String target) {
        if (target.contains(query)) {
            return 0;
        }

        int best = vectorManhattanDistance(query, target);
        if (target.length() > query.length()) {
            for (int start = 0; start <= target.length() - query.length(); start++) {
                best = Math.min(best, vectorManhattanDistance(
                        query,
                        target.substring(start, start + query.length())
                ));
            }
        }
        return best;
    }

    private static List<String> targets(CustomItemDefinition item, String setGroup) {
        List<String> targets = new ArrayList<>();
        targets.add(item.id());
        targets.add(item.itemKey());
        targets.add(TextUtils.plain(item.displayName()));
        targets.add(item.category());
        targets.addAll(item.categoryPath());
        targets.add(setGroup);
        targets.addAll(item.searchTokens());
        return targets.stream()
                .map(ItemSearch::normalize)
                .filter(target -> !target.isBlank())
                .distinct()
                .toList();
    }

    private static int vectorManhattanDistance(String left, String right) {
        int[] counts = new int[36];
        countCharacters(left, counts, 1);
        countCharacters(right, counts, -1);

        int distance = 0;
        for (int count : counts) {
            distance += Math.abs(count);
        }
        return distance;
    }

    private static void countCharacters(String text, int[] counts, int multiplier) {
        for (char character : text.toCharArray()) {
            if (character >= 'a' && character <= 'z') {
                counts[character - 'a'] += multiplier;
            } else if (character >= '0' && character <= '9') {
                counts[26 + character - '0'] += multiplier;
            }
        }
    }

    private record SearchEntry(CustomItemDefinition item, List<String> targets) {
        private int score(String query) {
            return targets.stream()
                    .mapToInt(target -> manhattanDistance(query, target))
                    .min()
                    .orElse(Integer.MAX_VALUE);
        }
    }

    private record SearchMatch(CustomItemDefinition item, int score) {
    }
}
