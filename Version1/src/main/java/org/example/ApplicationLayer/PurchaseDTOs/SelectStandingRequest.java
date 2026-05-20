package org.example.ApplicationLayer.PurchaseDTOs;

import java.util.UUID;

public class SelectStandingRequest {
    public int amount;
    public UUID areaID;
    public UUID userID;
    public boolean isConfirmedAge;
    public SelectStandingRequest() {}
}
