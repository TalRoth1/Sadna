package org.example.ApplicationLayer.dto.EventDTOs;

import java.util.UUID;

public class UpdateSittingAreaRequest {
    public String username;
    public UUID companyId;
    public double price;
    public int rows;
    public int seatsPerRow;

    public UpdateSittingAreaRequest() {}
}
