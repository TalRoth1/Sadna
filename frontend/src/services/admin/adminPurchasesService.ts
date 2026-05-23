import api from "../api";
import type {
    AdminPurchaseFilterType,
    GlobalPurchaseRecord,
} from "../../types/admin";

type PurchaseHistoryDto = {
    userId: string;
    eventId: string;
    ticketIds?: string[];
    eventName?: string;
    eventDate?: string;
    eventLocation?: string;
    ticketsAmount?: number;
    totalPrice?: number;
    paymentInfo?: string;
    purchaseDate?: string;

    buyerName?: string;
    companyId?: string;
    companyName?: string;
};

function buildPurchaseId(purchase: PurchaseHistoryDto, index: number): string {
    return [
        purchase.userId,
        purchase.eventId,
        purchase.purchaseDate,
        index,
    ]
        .filter(Boolean)
        .join("-");
}

function mapPurchase(purchase: PurchaseHistoryDto, index: number): GlobalPurchaseRecord {
    const ticketIds = purchase.ticketIds ?? [];

    return {
        id: buildPurchaseId(purchase, index),
        buyerId: purchase.userId,
        buyerName: purchase.buyerName || purchase.userId,
        companyId: purchase.companyId,
        companyName: purchase.companyName || "Unknown company",
        eventId: purchase.eventId,
        eventName: purchase.eventName || purchase.eventId,
        eventDate: purchase.eventDate,
        eventLocation: purchase.eventLocation,
        ticketIds,
        ticketsAmount: purchase.ticketsAmount ?? ticketIds.length,
        paymentInfo: purchase.paymentInfo,
        totalPrice: purchase.totalPrice ?? 0,
        purchaseDate: purchase.purchaseDate ?? "",
    };
}

export async function getGlobalPurchaseHistory(
    _userId: string,
): Promise<GlobalPurchaseRecord[]> {
    const response = await api.get("/admin/purchases");
    const purchases = response.data.data as PurchaseHistoryDto[];

    return purchases.map(mapPurchase);
}

export async function getFilteredPurchaseHistory(
    _userId: string,
    filterType: AdminPurchaseFilterType,
    filterId?: string,
): Promise<GlobalPurchaseRecord[]> {
    if (filterType === "all") {
        return getGlobalPurchaseHistory(_userId);
    }

    if (!filterId?.trim()) {
        throw new Error("Filter ID is required.");
    }

    const response = await api.get("/admin/purchases/filter", {
        params: {
            type: filterType,
            id: filterId.trim(),
        },
    });

    const purchases = response.data.data as PurchaseHistoryDto[];

    return purchases.map(mapPurchase);
}