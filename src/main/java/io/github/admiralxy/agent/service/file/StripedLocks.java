package io.github.admiralxy.agent.service.file;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public final class StripedLocks {

    private final ReadWriteLock[] locks;

    public StripedLocks(int stripes) {
        locks = new ReadWriteLock[stripes];
        for (int i = 0; i < stripes; i++) {
            locks[i] = new ReentrantReadWriteLock();
        }
    }

    public <T> T read(UUID key, Supplier<T> action) {
        return locked(lock(key).readLock(), action);
    }

    public <T> T write(UUID key, Supplier<T> action) {
        return locked(lock(key).writeLock(), action);
    }

    private ReadWriteLock lock(UUID key) {
        return locks[Math.floorMod(key.hashCode(), locks.length)];
    }

    private static <T> T locked(Lock lock, Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
