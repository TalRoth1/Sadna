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
    const data = response.data.data as AdminAnalyticsDto;

    return {
        // raw counts
        registeredUsersCount: data.registeredUsersCount,
        loggedInUsersCount:   data.loggedInUsersCount,
        activeCompaniesCount: data.activeCompaniesCount,
        activeQueuesCount:    data.activeQueuesCount,
        activePurchasesCount: data.activePurchasesCount,
        totalPurchasesCount:  data.totalPurchasesCount,
        createdAt:            data.createdAt,
        // dashboard display fields
        activeVisitors:        data.loggedInUsersCount,
        newSubscribersRate:    data.newSubscriberRatePerMin,
        ticketReservationRate: data.ticketReservationRatePerMin,
        ticketPurchaseRate:    data.ticketPurchaseRatePerMin,
        activeQueues:          data.activeQueuesCount,
    };
}
