package org.example.API;

import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationDTO;

public class NotificationStreamPayload {

    public String event;
    public NotificationDTO notification;

    public NotificationStreamPayload(String event, NotificationDTO notification) {
        this.event = event;
        this.notification = notification;
    }
}