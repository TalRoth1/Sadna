package org.example.ApplicationLayer.dto.ComapnyDTOs;

import java.time.LocalDate;

public class AddCouponRequest {
    public String username;
    public LocalDate fromDate;
    public LocalDate toDate;
    public float discountPercent;
    public String code;
}
