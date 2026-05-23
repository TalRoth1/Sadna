import api from "../api";
import type { SystemAnalytics } from "../../types/admin";

type AdminAnalyticsDto = {
    registeredUsersCount: number;
    loggedInUsersCount: number;
    activeCompaniesCount: number;
    activeQueuesCount: number;
    activePurchasesCount: number;
    totalPurchasesCount: number;
    createdAt: string;
};

export async function getSystemAnalytics(
    _userId: string,
): Promise<SystemAnalytics> {
    const response = await api.get("/admin/analytics");
    const data = response.data.data as AdminAnalyticsDto;

    return {
        registeredUsersCount: data.registeredUsersCount,
        loggedInUsersCount: data.loggedInUsersCount,
        activeCompaniesCount: data.activeCompaniesCount,
        activeQueuesCount: data.activeQueuesCount,
        activePurchasesCount: data.activePurchasesCount,
        totalPurchasesCount: data.totalPurchasesCount,
        createdAt: data.createdAt,
    };
}