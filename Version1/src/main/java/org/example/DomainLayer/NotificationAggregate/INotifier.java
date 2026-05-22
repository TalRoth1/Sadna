package org.example.DomainLayer.NotificationAggregate;

import java.util.UUID;

public interface INotifier {
    boolean notifyUser(UUID userId, String message);
    boolean notifyUser(String username, String message);
}
