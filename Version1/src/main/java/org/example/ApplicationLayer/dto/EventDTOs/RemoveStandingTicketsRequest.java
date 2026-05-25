package org.example.ApplicationLayer.dto.EventDTOs;

import java.util.UUID;

public class RemoveStandingTicketsRequest {
    public String username;
    public UUID companyId;
    public int count;

    public RemoveStandingTicketsRequest() {}
}
