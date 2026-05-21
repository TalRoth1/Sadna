package org.example.ApplicationLayer.dto.EventDTOs;

import java.time.LocalDate;
import java.util.UUID;

public class AddEventConditionalDiscountRequest {
    public String username;
    public UUID companyId;
    public LocalDate fromDate;
    public LocalDate toDate;
    public float discountPercent;
    public int requiredTickets;
    public int appliedTickets;

    public AddEventConditionalDiscountRequest() {}
}
