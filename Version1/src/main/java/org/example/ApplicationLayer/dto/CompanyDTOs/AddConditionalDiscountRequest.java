package org.example.ApplicationLayer.dto.CompanyDTOs;

import java.time.LocalDate;

public class AddConditionalDiscountRequest {
    public String username;
    public LocalDate fromDate;
    public LocalDate toDate;
    public float discountPercent;
    public int requiredTickets;
    public int appliedTickets;
}
