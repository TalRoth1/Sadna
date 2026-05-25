package org.example.ApplicationLayer.dto.CompanyDTOs;

import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.DiscountPolicyDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.PurchasePolicyDto;

/**
 * Company policy payload used by the company page.
 * Reuses the same flattened rule DTOs as the event details API.
 */
public class CompanyPoliciesResponse {
    public final java.util.UUID companyId;
    public final String companyName;
    public final PurchasePolicyDto purchasePolicy;
    public final DiscountPolicyDto discountPolicy;

    public CompanyPoliciesResponse(java.util.UUID companyId,
                                   String companyName,
                                   PurchasePolicyDto purchasePolicy,
                                   DiscountPolicyDto discountPolicy) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.purchasePolicy = purchasePolicy;
        this.discountPolicy = discountPolicy;
    }
}