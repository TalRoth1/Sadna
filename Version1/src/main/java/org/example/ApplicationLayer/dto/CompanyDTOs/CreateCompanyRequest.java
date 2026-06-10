package org.example.ApplicationLayer.dto.CompanyDTOs;

import org.example.DomainLayer.PolicyManagment.DiscountType;

public class CreateCompanyRequest {
    public String founderEmail;
    public String companyName;
    public DiscountType discountType;
}
