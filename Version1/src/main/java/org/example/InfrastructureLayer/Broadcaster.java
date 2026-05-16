package org.example.InfrastructureLayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Broadcaster {

    private final Executor executor = Executors.newSingleThreadExecutor();

    private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();

    public void register(String userId, Consumer<String> listener) {
        listeners
            .computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>())
            .add(listener);
    }

    public void unregister(String userId, Consumer<String> listener) {
        List<Consumer<String>> userListeners = listeners.get(userId);

        if (userListeners == null) {
            return;
        }

        userListeners.remove(listener);

        if (userListeners.isEmpty()) {
            listeners.remove(userId);
        }
    }

    public boolean broadcast(String userId, String message) {
        List<Consumer<String>> userListeners = listeners.get(userId);

        if (userListeners == null || userListeners.isEmpty()) {
            return false;
        }

        for (Consumer<String> listener : userListeners) {
            executor.execute(() -> listener.accept(message));
        }

        return true;
    }
}