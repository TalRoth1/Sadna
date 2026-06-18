package org.example.AdditionalTests;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.InfrastructureLayer.CompanyRepository;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class CompanyRepositoryAdditionalTests {

    @Test
    public void createCompany_validatesAllRequiredFieldsBeforePersisting() {
        CompanyRepository repo = new CompanyRepository();

        assertThrows(IllegalArgumentException.class,
                () -> repo.createCompany(null, "Acme", DiscountType.ALL));
        assertThrows(IllegalArgumentException.class,
                () -> repo.createCompany("", "Acme", DiscountType.ALL));
        assertThrows(IllegalArgumentException.class,
                () -> repo.createCompany("founder@example.com", null, DiscountType.ALL));
        assertThrows(IllegalArgumentException.class,
                () -> repo.createCompany("founder@example.com", "", DiscountType.ALL));
        assertThrows(IllegalArgumentException.class,
                () -> repo.createCompany("founder@example.com", "Acme", null));

        assertTrue(repo.getAll().isEmpty());
    }

    @Test
    public void saveFindAllAndActive_coverPresentMissingAndClosedCompanies() {
        CompanyRepository repo = new CompanyRepository();
        Company active = new Company("active@example.com", "Active", DiscountType.ALL);
        Company closed = new Company("closed@example.com", "Closed", DiscountType.ALL);
        closed.AdminClose();

        repo.save(active);
        repo.save(closed);

        assertEquals(Optional.of(active), repo.findByID(active.getId()));
        assertEquals(Optional.of(closed), repo.findByID(closed.getId()));
        assertEquals(Optional.empty(), repo.findByID(UUID.randomUUID()));
        assertEquals(List.of(active), repo.getAllActive());
        assertEquals(2, repo.getAll().size());

        assertThrows(IllegalArgumentException.class, () -> repo.save(null));
    }
}
