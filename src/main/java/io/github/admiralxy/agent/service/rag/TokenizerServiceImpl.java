package io.github.admiralxy.agent.service.rag;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TokenizerServiceImpl implements TokenizerService {

    private static final String MAX_TOKENS_NOT_POSITIVE = "maxTokens must be greater than 0";
    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry()
            .getEncoding(EncodingType.CL100K_BASE);

    @Override
    public int countTokens(String text) {
        return ENCODING.encode(text).size();
    }

    @Override
    public String truncateToTokens(String text, int maxTokens) {
        IntArrayList tokens = ENCODING.encode(text);
        return tokens.size() <= maxTokens ? text : decode(tokens, 0, maxTokens);
    }

    @Override
    public List<String> splitToTokenChunks(String text, int maxTokens) {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException(MAX_TOKENS_NOT_POSITIVE);
        }
        IntArrayList tokens = ENCODING.encode(text);
        if (tokens.size() <= maxTokens) {
            return List.of(text);
        }
        List<String> parts = new ArrayList<>((tokens.size() + maxTokens - 1) / maxTokens);
        for (int start = 0; start < tokens.size(); start += maxTokens) {
            parts.add(decode(tokens, start, Math.min(start + maxTokens, tokens.size())));
        }
        return parts;
    }

    private static String decode(IntArrayList tokens, int from, int to) {
        IntArrayList segment = new IntArrayList(to - from);
        for (int i = from; i < to; i++) {
            segment.add(tokens.get(i));
        }
        return ENCODING.decode(segment);
    }
}
