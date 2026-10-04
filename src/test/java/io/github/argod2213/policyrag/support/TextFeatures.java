package io.github.argod2213.policyrag.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tiny tokenizer shared by the deterministic test doubles. */
final class TextFeatures {

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "for", "is", "are", "be", "by", "with", "at",
            "as", "it", "its", "i", "my", "me", "we", "our", "you", "your", "do", "does", "can", "how", "what",
            "which", "who", "when", "many", "much", "must", "may", "per", "that", "this", "from", "into", "after",
            "before", "than", "there", "their", "any", "all", "every", "get", "about", "policy", "company");

    private TextFeatures() {
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9$]+")) {
            if (raw.isEmpty() || STOPWORDS.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    private static String stem(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 3 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
