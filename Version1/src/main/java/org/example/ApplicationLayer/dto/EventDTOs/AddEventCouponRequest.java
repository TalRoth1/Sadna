package org.example.ApplicationLayer.dto.EventDTOs;

import java.time.LocalDate;
import java.util.UUID;

public class AddEventCouponRequest {
    public String username;
    public UUID companyId;
    public LocalDate fromDate;
    public LocalDate toDate;
    public float discountPercent;
    public String code;

    public AddEventCouponRequest() {}
}
