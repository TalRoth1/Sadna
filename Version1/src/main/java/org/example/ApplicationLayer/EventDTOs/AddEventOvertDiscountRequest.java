package org.example.ApplicationLayer.EventDTOs;

import java.time.LocalDate;
import java.util.UUID;

public class AddEventOvertDiscountRequest {
    public String username;
    public UUID companyId;
    public LocalDate fromDate;
    public LocalDate toDate;
    public float discountPercent;

    public AddEventOvertDiscountRequest() {}
}
