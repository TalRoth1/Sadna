package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationDTO;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class Broadcaster {

    private final Executor executor = Executors.newCachedThreadPool();

    private final Map<String, List<Consumer<NotificationDTO>>> listeners = new ConcurrentHashMap<>();

    public void register(String userId, Consumer<NotificationDTO> listener) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener is required");
        }

        listeners
                .computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    public void unregister(String userId, Consumer<NotificationDTO> listener) {
        List<Consumer<NotificationDTO>> userListeners = listeners.get(userId);

        if (userListeners == null) {
            return;
        }

        userListeners.remove(listener);

        if (userListeners.isEmpty()) {
            listeners.remove(userId);
        }
    }

    public boolean broadcast(String userId, NotificationDTO notification) {
        List<Consumer<NotificationDTO>> userListeners = listeners.get(userId);

        if (userListeners == null || userListeners.isEmpty()) {
            return false;
        }

        for (Consumer<NotificationDTO> listener : userListeners) {
            executor.execute(() -> listener.accept(notification));
        }

        return true;
    }

    public boolean hasListeners(String userId) {
        List<Consumer<NotificationDTO>> userListeners = listeners.get(userId);
        return userListeners != null && !userListeners.isEmpty();
    }
}