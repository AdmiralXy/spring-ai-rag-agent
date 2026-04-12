package io.github.admiralxy.agent.service.impl;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import io.github.admiralxy.agent.service.TokenizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenizerServiceImpl implements TokenizerService {

    private static final Encoding encoding = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    public int countTokens(String text) {
        return encoding.encode(text).size();
    }

    public String truncateToTokens(String text, int maxTokens) {
        IntArrayList tokens = encoding.encode(text);
        if (tokens.size() <= maxTokens) {
            return text;
        }

        int[] raw = tokens.toArray();
        int[] truncatedArr = Arrays.copyOf(raw, maxTokens);

        IntArrayList truncated = new IntArrayList(truncatedArr.length);
        for (int t : truncatedArr) {
            truncated.add(t);
        }

        return encoding.decode(truncated);
    }

    @Override
    public List<String> splitToTokenChunks(String text, int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be greater than 0");
        }

        IntArrayList tokens = encoding.encode(text);
        int tokenCount = tokens.size();
        if (tokenCount <= maxTokens) {
            return List.of(text);
        }

        int[] raw = tokens.toArray();
        List<String> parts = new ArrayList<>((tokenCount + maxTokens - 1) / maxTokens);
        for (int start = 0; start < tokenCount; start += maxTokens) {
            int endExclusive = Math.min(start + maxTokens, tokenCount);
            IntArrayList segment = new IntArrayList(endExclusive - start);
            for (int i = start; i < endExclusive; i++) {
                segment.add(raw[i]);
            }
            parts.add(encoding.decode(segment));
        }
        return parts;
    }
}
