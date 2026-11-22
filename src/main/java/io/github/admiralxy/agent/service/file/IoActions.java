package io.github.admiralxy.agent.service.file;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class IoActions {

    private IoActions() {
    }

    public static <T> T unchecked(IoSupplier<T> action) {
        try {
            return action.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void unchecked(IoRunnable action) {
        unchecked(() -> {
            action.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface IoSupplier<T> {
        T get() throws IOException;
    }

    @FunctionalInterface
    public interface IoRunnable {
        void run() throws IOException;
    }
}
