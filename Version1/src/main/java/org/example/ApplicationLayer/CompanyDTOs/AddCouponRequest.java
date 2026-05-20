package org.example.ApplicationLayer.CompanyDTOs;

import java.time.LocalDate;

public class AddCouponRequest {
    public String username;
    public LocalDate fromDate;
    public LocalDate toDate;
    public float discountPercent;
    public String code;
}
