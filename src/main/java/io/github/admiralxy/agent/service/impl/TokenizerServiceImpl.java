package io.github.admiralxy.agent.service.impl;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import io.github.admiralxy.agent.service.TokenizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

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
}
