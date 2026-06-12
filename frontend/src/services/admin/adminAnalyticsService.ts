import api from "../api";
import type { SystemAnalytics } from "../../types/admin";

// Mirrors AdminAnalyticsDTO on the backend — all fields including the three
// new real-time rate fields added in the analytics feature.
type AdminAnalyticsDto = {
    registeredUsersCount: number;
    loggedInUsersCount: number;
    activeCompaniesCount: number;
    activeQueuesCount: number;
    activePurchasesCount: number;
    totalPurchasesCount: number;
    newSubscriberRatePerMin: number;
    ticketReservationRatePerMin: number;
    ticketPurchaseRatePerMin: number;
    createdAt: string;
};

export async function getSystemAnalytics(
    _userId: string,
): Promise<SystemAnalytics> {
    const response = await api.get("/admin/analytics");

    console.log("[adminAnalyticsService] full response:", response.data);

    const data = response.data.data as AdminAnalyticsDto;

    console.log("[adminAnalyticsService] mapped data:", data);

    return {
        registeredUsersCount: data.registeredUsersCount ?? 0,
        loggedInUsersCount: data.loggedInUsersCount ?? 0,
        activeCompaniesCount: data.activeCompaniesCount ?? 0,
        activeQueuesCount: data.activeQueuesCount ?? 0,
        activePurchasesCount: data.activePurchasesCount ?? 0,
        totalPurchasesCount: data.totalPurchasesCount ?? 0,
        createdAt: data.createdAt ?? "",

        activeVisitors: data.loggedInUsersCount ?? 0,
        newSubscribersRate: data.newSubscriberRatePerMin ?? 0,
        ticketReservationRate: data.ticketReservationRatePerMin ?? 0,
        ticketPurchaseRate: data.ticketPurchaseRatePerMin ?? 0,
        activeQueues: data.activeQueuesCount ?? 0,
    };
}
