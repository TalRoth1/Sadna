package org.example.ApplicationLayer.dto.EventDTOs;

import java.time.LocalDate;
import java.util.UUID;

public class EditEventDiscountRequest {
    public String username;
    public UUID companyId;

    /**
     * NONE | OVERT | COUPON | CONDITIONAL
     */
    public String discountType;

    public LocalDate fromDate;
    public LocalDate toDate;
    public Float discountPercent;

    // COUPON
    public String code;

    // CONDITIONAL
    public Integer requiredTickets;
    public Integer appliedTickets;

    public EditEventDiscountRequest() {}
}