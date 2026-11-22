package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.RagSearchHit;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
public class RagContextAssembler {

    public static final String SOURCE_HEADER = "Source: %s\n\n";

    private static final int MAX_OVERLAP_CHARS = 1500;
    private static final int MIN_OVERLAP_CHARS = 16;
    private static final String GAP = "\n[…]\n";
    private static final String LINE = "\n";
    private static final String DOCUMENT_OPEN = "<document index=\"%d\"%s%s>\n";
    private static final String DOCUMENT_CLOSE = "\n</document>\n\n";
    private static final String SOURCE_ATTRIBUTE = " source=\"%s\"";
    private static final String SPACE_ATTRIBUTE = " space=\"%s\"";
    private static final String ID_KEY = "id:";
    private static final String SOURCE_KEY = "src:%s|%s";
    private static final String QUOTE = "\"";
    private static final String APOSTROPHE = "'";
    private static final String SPACE = " ";

    private final TokenizerService tokenizerService;

    public String assemble(List<RagSearchHit> ranked,
                           BiFunction<RagSearchHit, Set<Integer>, List<RagSearchHit>> neighbours,
                           Map<String, String> spaceNames,
                           int maxTokens,
                           int expandTopHits) {
        if (ranked.isEmpty() || maxTokens <= 0) {
            return StringUtils.EMPTY;
        }
        Selection selection = new Selection(maxTokens);
        for (int rank = 0; rank < ranked.size() && !selection.isFull(); rank++) {
            selection.addOrCut(ranked.get(rank), rank);
        }
        expand(selection, neighbours, expandTopHits);
        return render(selection.chunks.values(), spaceNames);
    }

    static int overlap(String previous, String next) {
        int max = Math.min(MAX_OVERLAP_CHARS, Math.min(previous.length(), next.length()));
        for (int k = max; k >= MIN_OVERLAP_CHARS; k--) {
            if (previous.endsWith(next.substring(0, k))) {
                return k;
            }
        }
        return 0;
    }

    private void expand(Selection selection,
                        BiFunction<RagSearchHit, Set<Integer>, List<RagSearchHit>> neighbours,
                        int expandTopHits) {
        List<Selected> best = selection.chunks.values().stream()
                .filter(s -> s.hit().hasPart() && s.hit().source() != null)
                .limit(Math.max(0, expandTopHits))
                .toList();
        for (Selected s : best) {
            int part = s.hit().part();
            Set<Integer> wanted = part > 0 ? Set.of(part - 1, part + 1) : Set.of(part + 1);
            neighbours.apply(s.hit(), wanted).forEach(neighbour -> selection.addIfFits(neighbour, s.rank()));
        }
    }

    private String render(Iterable<Selected> chunks, Map<String, String> spaceNames) {
        Map<String, List<Selected>> groups = new LinkedHashMap<>();
        for (Selected s : chunks) {
            String key = s.hit().source() == null
                    ? ID_KEY + s.hit().id()
                    : SOURCE_KEY.formatted(s.hit().doc(), s.hit().source());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        List<List<Selected>> ordered = groups.values().stream()
                .sorted(Comparator.comparingInt(g -> g.stream().mapToInt(Selected::rank).min().orElse(0)))
                .toList();

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (List<Selected> group : ordered) {
            group.sort(Comparator.comparingInt(s -> s.hit().part()));
            String text = merge(group);
            if (StringUtils.isBlank(text)) {
                continue;
            }
            RagSearchHit first = group.getFirst().hit();
            String space = first.space() == null ? null : spaceNames.getOrDefault(first.space(), first.space());
            sb.append(DOCUMENT_OPEN.formatted(index++, attribute(SOURCE_ATTRIBUTE, first.source()),
                            attribute(SPACE_ATTRIBUTE, space)))
                    .append(text.strip())
                    .append(DOCUMENT_CLOSE);
        }
        return sb.toString().strip();
    }

    private static String merge(List<Selected> group) {
        StringBuilder merged = new StringBuilder();
        Integer previousPart = null;
        String previousText = null;
        for (Selected s : group) {
            String text = stripHeader(s.hit());
            int part = s.hit().part();
            if (previousText == null) {
                merged.append(text);
            } else if (part == previousPart + 1) {
                merged.append(LINE).append(text.substring(overlap(previousText, text)));
            } else if (part == previousPart) {
                merged.append(text);
            } else {
                merged.append(GAP).append(text);
            }
            previousPart = part;
            previousText = text;
        }
        return merged.toString();
    }

    private static String stripHeader(RagSearchHit hit) {
        String content = hit.content();
        if (hit.source() == null) {
            return content;
        }
        return StringUtils.removeStart(content, SOURCE_HEADER.formatted(hit.source()));
    }

    private static String attribute(String template, @Nullable String value) {
        if (value == null) {
            return StringUtils.EMPTY;
        }
        return template.formatted(value.replace(QUOTE, APOSTROPHE).replace(LINE, SPACE));
    }

    private record Selected(RagSearchHit hit, int rank) {
    }

    private final class Selection {

        private final Map<String, Selected> chunks = new LinkedHashMap<>();
        private final int maxTokens;
        private int used;

        private Selection(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        private boolean isFull() {
            return used >= maxTokens;
        }

        private void addOrCut(RagSearchHit hit, int rank) {
            if (addIfFits(hit, rank) || !chunks.isEmpty() || StringUtils.isBlank(hit.content())) {
                return;
            }
            chunks.put(hit.id(), new Selected(hit.withContent(tokenizerService.truncateToTokens(hit.content(),
                    maxTokens)), rank));
            used = maxTokens;
        }

        private boolean addIfFits(RagSearchHit hit, int rank) {
            if (chunks.containsKey(hit.id()) || StringUtils.isBlank(hit.content())) {
                return false;
            }
            int tokens = tokenizerService.countTokens(hit.content());
            if (used + tokens > maxTokens) {
                return false;
            }
            chunks.put(hit.id(), new Selected(hit, rank));
            used += tokens;
            return true;
        }
    }
}
