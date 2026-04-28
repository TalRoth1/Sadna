package org.example.DomainLayer.CompanyAggregate;

public enum CompanyPermission {
    MANAGE_INVENTORY("Manage event inventory"),
    CONFIGURE_LAYOUT("Configure event layout and event map"),
    MANAGE_POLICIES("Manage purchase and discount policies"),
    CUSTOMER_SERVICE("Receive and respond to customer inquiries"),
    VIEW_HISTORY("View order and purchase history"),
    REPORTS_GENERATION("Generate sales reports");

    private final String description;

    CompanyPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
