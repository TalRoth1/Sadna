package org.example.ApplicationLayer.dto.AdminDTOs;

import java.util.UUID;

public class AdminCompanyDTO {
    public UUID id;
    public String name;
    public String status;
    public int ownersCount;
    public int managersCount;

    public AdminCompanyDTO() {
    }
}