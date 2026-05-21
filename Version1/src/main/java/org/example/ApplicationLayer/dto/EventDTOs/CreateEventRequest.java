package org.example.ApplicationLayer.dto.EventDTOs;

import java.time.LocalDateTime;
import java.util.UUID;

import org.example.DomainLayer.EventAggregate.EventStatus;

public class CreateEventRequest {
    public UUID companyId;
    public String name;
    public LocalDateTime date;
    public String location;
    public String artist;
    public String type;
    public EventStatus status;

    public CreateEventRequest() {}
}