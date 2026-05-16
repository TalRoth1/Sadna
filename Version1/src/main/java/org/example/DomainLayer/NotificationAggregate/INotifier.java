package org.example.DomainLayer.NotificationAggregate;

public interface INotifier {
    boolean notifyUser(String userId, String message);
}
