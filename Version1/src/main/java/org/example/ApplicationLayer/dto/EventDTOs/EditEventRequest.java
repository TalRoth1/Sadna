package org.example.ApplicationLayer.dto.EventDTOs;

import java.time.LocalDateTime;

import org.example.DomainLayer.EventAggregate.EventStatus;

/**
 * For editing an event. All fields are optional —
 * only the non-null ones will be applied.
 */
public class EditEventRequest {
    public String name;
    public LocalDateTime date;
    public String location;
    public String artist;
    public String type;
    public EventStatus status;

    public EditEventRequest() {}
}
