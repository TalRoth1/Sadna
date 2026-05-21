package org.example.ApplicationLayer.dto.CompanyDTOs;

import java.time.LocalDate;

public class AddOvertDiscountRequest {
    public String username;
    public LocalDate fromDate;
    public LocalDate toDate;
    public float discountPercent;
}
