package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.RagSearchHit;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime.Embedder;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class HybridRagRetriever {

    private static final int RRF_K = 60;
    private static final double VECTOR_WEIGHT = 1.0;
    private static final double CONTEXTUAL_QUERY_WEIGHT = 0.5;
    private static final int MAX_KEYWORDS = 24;
    private static final int MIN_WORD_LENGTH = 3;
    private static final int MIN_NUMERIC_WORD_LENGTH = 2;
    private static final String PREFIX_MATCH = ":*";
    private static final String OR = " | ";
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "are", "was", "with", "that", "this", "from", "what", "how", "why", "which",
            "who", "where", "when", "does", "can", "you", "your", "about", "into", "there", "have", "has", "not",
            "but", "all", "any", "use", "using", "please", "tell", "show", "give", "explain",
            "как", "что", "это", "для", "или", "так", "его", "она", "они", "оно", "мне", "меня", "тебя", "ты",
            "вы", "мы", "где", "когда", "почему", "зачем", "какой", "какая", "какие", "каких", "который",
            "которая", "которые", "есть", "был", "была", "были", "будет", "нет", "все", "всё", "уже", "ещё",
            "еще", "при", "про", "над", "под", "без", "через", "чтобы", "если", "тоже", "также", "только",
            "можно", "нужно", "надо", "расскажи", "покажи", "объясни", "пожалуйста"
    );

    private final RagDocumentRepository repository;

    public List<RagSearchHit> search(Embedder embedder, List<UUID> spaceIds, String query,
                                     @Nullable String contextualQuery, int topK, double keywordWeight) {
        if (spaceIds.isEmpty() || StringUtils.isBlank(query) || topK <= 0) {
            return List.of();
        }
        int dimensions = embedder.settings().getDimensions();
        Map<String, Fused> fused = new LinkedHashMap<>();
        fuse(fused, repository.vectorSearch(spaceIds, dimensions, embedder.embed(query), topK), VECTOR_WEIGHT);
        if (StringUtils.isNotBlank(contextualQuery)) {
            fuse(fused, repository.vectorSearch(spaceIds, dimensions, embedder.embed(contextualQuery), topK),
                    CONTEXTUAL_QUERY_WEIGHT);
        }
        String tsQuery = toTsQuery(query);
        if (keywordWeight > 0 && !tsQuery.isEmpty()) {
            fuse(fused, repository.keywordSearch(spaceIds, tsQuery, topK), keywordWeight);
        }
        return fused.values().stream()
                .sorted(Comparator.comparingDouble(Fused::score).reversed())
                .map(Fused::hit)
                .toList();
    }

    public List<RagSearchHit> neighbours(RagSearchHit hit, Collection<Integer> parts) {
        return repository.neighbours(hit, parts);
    }

    static String toTsQuery(String query) {
        Set<String> words = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(query.toLowerCase(Locale.ROOT));
        while (matcher.find() && words.size() < MAX_KEYWORDS) {
            String word = matcher.group();
            boolean hasDigit = word.chars().anyMatch(Character::isDigit);
            boolean longEnough = word.length() >= MIN_WORD_LENGTH
                    || (hasDigit && word.length() >= MIN_NUMERIC_WORD_LENGTH);
            if (longEnough && !STOP_WORDS.contains(word)) {
                words.add(word);
            }
        }
        return String.join(OR, words.stream().map(word -> word + PREFIX_MATCH).toList());
    }

    private static void fuse(Map<String, Fused> fused, List<RagSearchHit> ranked, double weight) {
        for (int rank = 0; rank < ranked.size(); rank++) {
            RagSearchHit hit = ranked.get(rank);
            double score = weight / (RRF_K + rank + 1);
            fused.merge(hit.id(), new Fused(hit, score), (a, b) -> new Fused(a.hit(), a.score() + b.score()));
        }
    }

    private record Fused(RagSearchHit hit, double score) {
    }
}
