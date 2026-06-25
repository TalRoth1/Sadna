package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.util.List;
import java.util.UUID;

public class UpdateTicketsRequest {
    public List<UUID> ticketIDs;
    public int standingAmount;
    public UUID standingAreaID;

    public UpdateTicketsRequest() {}
}