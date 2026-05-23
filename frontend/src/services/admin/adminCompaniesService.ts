import api from "../api";
import type { ProductionCompany } from "../../types/admin";

const mockCompanies: ProductionCompany[] = [
    {
        id: "company-1",
        name: "Live Nation Israel",
        status: "active",
        ownersCount: 2,
        managersCount: 4,
    },
];

export async function getProductionCompanies(
    _userId: string,
): Promise<ProductionCompany[]> {
    return mockCompanies;
}

export async function closeProductionCompany(
    _userId: string,
    companyId: string,
): Promise<void> {
    await api.delete(`/admin/companies/${companyId}`);
}