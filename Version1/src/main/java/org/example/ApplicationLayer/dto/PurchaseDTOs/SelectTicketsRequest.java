package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.util.List;
import java.util.UUID;

public class SelectTicketsRequest {
    public List<UUID> ticketIDs;
    public int standingAmount;
    public UUID standingAreaID;
    public UUID userID;
    public boolean isConfirmedAge;
    public String accessCode;

    public SelectTicketsRequest() {}
}