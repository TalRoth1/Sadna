package org.example.DomainLayer.CompanyAggregate;

import java.util.UUID;

public class OwnerInvetation extends Invitation {
    public OwnerInvetation(String appointerUsername, String appointeeUsername, UUID companyId) {
        super(appointerUsername, appointeeUsername, companyId);
    }   
}
