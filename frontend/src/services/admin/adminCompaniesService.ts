import type { ProductionCompany } from "../../types/admin";
import { verifyPlatformAdmin } from "./adminAuthService";

const mockCompanies: ProductionCompany[] = [
    {
        id: "company-1",
        name: "Live Nation Israel",
        status: "active",
        ownersCount: 2,
        managersCount: 4,
    },
    {
        id: "company-2",
        name: "Urban Events",
        status: "active",
        ownersCount: 1,
        managersCount: 2,
    },
];

// TODO: Replace this mock implementation with a real server call.
// The server must verify that userId belongs to a platform admin before returning companies.
export async function getProductionCompanies(
    userId: string,
): Promise<ProductionCompany[]> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    return mockCompanies;
}

// TODO: Replace this mock implementation with a real server command.
// The server must verify admin permissions, close the selected company,
// send notifications to owners/managers, and cancel relevant company roles.
export async function closeProductionCompany(
    userId: string,
    companyId: string,
): Promise<void> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    console.log("Close company request:", { userId, companyId });
}