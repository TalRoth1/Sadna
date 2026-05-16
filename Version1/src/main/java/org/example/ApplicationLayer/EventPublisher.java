package org.example.ApplicationLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.example.DomainLayer.Events.IDomainEvent;

public class EventPublisher {

    private final List<Consumer<IDomainEvent>> listeners = new ArrayList<>();

    public void subscribe(Consumer<IDomainEvent> listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener is required");
        }

        listeners.add(listener);
    }

    public void publish(IDomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event is required");
        }

        for (Consumer<IDomainEvent> listener : listeners) {
            listener.accept(event);
        }
    }
}