package org.example.ApplicationLayer.dto.EventDTOs;

import java.time.LocalDateTime;
import java.util.UUID;

import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.PolicyManagment.DiscountType;

public class CreateEventRequest {
    public UUID companyId;
    public String eventManagerEmail;
    public String name;
    public LocalDateTime date;
    public String location;
    public String artist;
    public String type;
    public EventStatus status;
    public String description;
    public DiscountType discountType;

    // probably old fields, can stay for now if other code still uses them
    public Float ticketPrice;
    public Integer availableTickets;

    // new field
    public CreateLotteryRequest lottery;

    public CreateEventRequest() {}
}