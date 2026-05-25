package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.util.UUID;

public class SelectStandingRequest {
    public int amount;
    public UUID areaID;
    public UUID userID;
    public boolean isConfirmedAge;
    public String accessCode;
    public SelectStandingRequest() {}
}
