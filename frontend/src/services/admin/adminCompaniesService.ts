import api from "../api";
import type { ProductionCompany } from "../../types/admin";

type AdminCompanyDto = {
    id: string;
    name: string;
    status: string;
    ownersCount: number;
    managersCount: number;
};

function mapCompanyStatus(status: string): ProductionCompany["status"] {
    const normalizedStatus = status.toLowerCase();

    if (normalizedStatus === "closed" || normalizedStatus === "inactive") {
        return "closed";
    }

    return "active";
}

function mapCompany(company: AdminCompanyDto): ProductionCompany {
    return {
        id: company.id,
        name: company.name,
        status: mapCompanyStatus(company.status),
        ownersCount: company.ownersCount ?? 0,
        managersCount: company.managersCount ?? 0,
    };
}

export async function getProductionCompanies(
    _userId: string,
): Promise<ProductionCompany[]> {
    const response = await api.get("/admin/companies");
    const companies = response.data.data as AdminCompanyDto[];

    return companies
        .map(mapCompany)
        .filter((company) => company.status === "active");
}

export async function closeProductionCompany(
    _userId: string,
    companyId: string,
): Promise<void> {
    await api.delete(`/admin/companies/${companyId}`);
}