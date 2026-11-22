package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.ChatAttachment;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;

public final class ChatImages {

    public static final int MAX_IMAGES = 10;
    public static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final String DATA_URL_PREFIX = "data:";
    private static final char DATA_URL_SEPARATOR = ',';
    private static final int MAX_NAME_LENGTH = 200;
    private static final int BYTES_IN_MB = 1024 * 1024;
    private static final String NOT_SUPPORTED = "Model '%s' does not accept images. "
            + "Turn on \"Images\" in its settings if it supports them.";
    private static final String TOO_MANY = "At most %d images per message";
    private static final String UNSUPPORTED_TYPE = "Unsupported image type: %s";
    private static final String NOT_BASE64 = "Image is not valid base64: %s";
    private static final String TOO_LARGE = "Images must be at most %d MB: %s";

    private ChatImages() {
    }

    public static List<ChatAttachment> validate(ChatModelEntity model, @Nullable List<ChatAttachment> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        if (!model.isVision()) {
            throw new IllegalArgumentException(NOT_SUPPORTED.formatted(model.getLabel()));
        }
        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException(TOO_MANY.formatted(MAX_IMAGES));
        }
        List<ChatAttachment> result = new ArrayList<>(images.size());
        for (ChatAttachment image : images) {
            result.add(validate(image));
        }
        return result;
    }

    private static ChatAttachment validate(ChatAttachment image) {
        String name = StringUtils.defaultString(image.name());
        String mimeType = StringUtils.lowerCase(StringUtils.trimToEmpty(image.mimeType()));
        if (!TYPES.contains(mimeType)) {
            throw new IllegalArgumentException(UNSUPPORTED_TYPE.formatted(image.mimeType()));
        }
        String data = stripDataUrl(StringUtils.deleteWhitespace(StringUtils.defaultString(image.data())));
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(NOT_BASE64.formatted(name));
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(TOO_LARGE.formatted(MAX_IMAGE_BYTES / BYTES_IN_MB, name));
        }
        return new ChatAttachment(mimeType, data, StringUtils.abbreviate(image.name(), MAX_NAME_LENGTH));
    }

    private static String stripDataUrl(String data) {
        int separator = data.indexOf(DATA_URL_SEPARATOR);
        return data.startsWith(DATA_URL_PREFIX) && separator > 0 ? data.substring(separator + 1) : data;
    }
}
