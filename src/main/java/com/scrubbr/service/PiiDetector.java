package com.scrubbr.service;

import com.scrubbr.model.PiiFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds and masks PII (emails, phones, cards, SSNs, IPs) in plain text. */
@Service
public class PiiDetector {

    public enum PiiType {
        EMAIL("Email address",
                Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
        CREDIT_CARD("Credit-card number",
                Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b")),
        SSN("US Social Security number",
                Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")),
        PHONE("Phone number",
                Pattern.compile("(?:\\+?\\d{1,3}[ .-]?)?(?:\\(\\d{2,4}\\)[ .-]?)?\\d{3}[ .-]?\\d{3,4}[ .-]?\\d{0,4}")),
        IP_ADDRESS("IP address",
                Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b"));

        private final String label;
        private final Pattern pattern;

        PiiType(String label, Pattern pattern) {
            this.label = label;
            this.pattern = pattern;
        }

        public String label() {
            return label;
        }

        public Pattern pattern() {
            return pattern;
        }
    }

    public record Match(PiiType type, String text, int start, int end) {
    }

    public List<Match> findMatches(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Match> all = new ArrayList<>();
        for (PiiType type : PiiType.values()) {
            Matcher m = type.pattern().matcher(text);
            while (m.find()) {
                if (isPlausible(type, m.group().trim())) {
                    all.add(new Match(type, m.group(), m.start(), m.end()));
                }
            }
        }
        return resolveOverlaps(all);
    }

    /** Groups raw matches by value, counts them, and masks each for display. */
    public List<PiiFinding> summarise(List<Match> matches, int page) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, PiiType> types = new LinkedHashMap<>();
        for (Match match : matches) {
            String key = match.type().name() + "|" + match.text().trim();
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            types.putIfAbsent(key, match.type());
        }
        List<PiiFinding> findings = new ArrayList<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            PiiType type = types.get(e.getKey());
            String raw = e.getKey().substring(e.getKey().indexOf('|') + 1);
            findings.add(new PiiFinding(type.label(), mask(raw), e.getValue()[0], page));
        }
        return findings;
    }

    /** When matches overlap, keep the longest (a card beats a phone). */
    private List<Match> resolveOverlaps(List<Match> matches) {
        matches.sort((a, b) -> {
            int byLength = Integer.compare(b.end() - b.start(), a.end() - a.start());
            return byLength != 0 ? byLength : Integer.compare(a.start(), b.start());
        });
        List<Match> kept = new ArrayList<>();
        for (Match candidate : matches) {
            boolean overlaps = kept.stream().anyMatch(k ->
                    candidate.start() < k.end() && k.start() < candidate.end());
            if (!overlaps) {
                kept.add(candidate);
            }
        }
        kept.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return kept;
    }

    private boolean isPlausible(PiiType type, String value) {
        return switch (type) {
            case CREDIT_CARD -> {
                String digits = value.replaceAll("\\D", "");
                yield digits.length() >= 13 && digits.length() <= 19 && luhnValid(digits);
            }
            case PHONE -> {
                String digits = value.replaceAll("\\D", "");
                yield digits.length() >= 7 && digits.length() <= 15;
            }
            default -> true;
        };
    }

    private boolean luhnValid(String digits) {
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    String mask(String value) {
        String trimmed = value.trim();
        if (trimmed.contains("@")) {
            int at = trimmed.indexOf('@');
            String name = trimmed.substring(0, at);
            String visible = name.isEmpty() ? "" : name.substring(0, 1);
            return visible + "***" + trimmed.substring(at);
        }
        if (trimmed.length() <= 4) {
            return "****";
        }
        int reveal = Math.min(4, trimmed.length() - 4);
        return "•".repeat(trimmed.length() - reveal) + trimmed.substring(trimmed.length() - reveal);
    }
}
