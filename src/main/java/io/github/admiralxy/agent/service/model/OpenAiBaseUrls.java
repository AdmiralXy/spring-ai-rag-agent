package io.github.admiralxy.agent.service.model;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.regex.Pattern;

public final class OpenAiBaseUrls {

    private static final Pattern VERSION_SUFFIX = Pattern.compile(".*/v\\d+(beta\\d*|alpha\\d*)?$");
    private static final String VERSION = "/v1";
    private static final String SLASH = "/";

    private OpenAiBaseUrls() {
    }

    public static String normalize(String baseUrl) {
        if (StringUtils.isBlank(baseUrl)) {
            return null;
        }
        String url = Strings.CS.removeEnd(baseUrl.trim(), SLASH);
        return VERSION_SUFFIX.matcher(url).matches() ? url : url + VERSION;
    }
}
