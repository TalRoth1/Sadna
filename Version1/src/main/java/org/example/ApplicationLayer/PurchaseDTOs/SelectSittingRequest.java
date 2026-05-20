package org.example.ApplicationLayer.PurchaseDTOs;

import java.util.List;
import java.util.UUID;

public class SelectSittingRequest {
    public List<UUID> ticketIDs;
    public UUID userID;
    public boolean isConfirmedAge;
    public SelectSittingRequest() {}
}