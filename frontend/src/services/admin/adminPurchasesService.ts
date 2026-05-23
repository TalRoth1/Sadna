import api from "../api";
import type { GlobalPurchaseRecord } from "../../types/admin";

type PurchaseHistoryDto = {
    userId: string;
    eventId: string;
    ticketIds: string[];
    eventName: string;
    eventDate: string;
    eventLocation: string;
    ticketsAmount: number;
    totalPrice: number;
    paymentInfo: string;
    purchaseDate: string;
};

export async function getGlobalPurchaseHistory(
    _userId: string,
): Promise<GlobalPurchaseRecord[]> {
    const response = await api.get("/admin/purchases");
    const purchases = response.data.data as PurchaseHistoryDto[];

    return purchases.map((purchase, index) => ({
        id: `${purchase.userId}-${purchase.eventId}-${purchase.purchaseDate}-${index}`,
        buyerName: purchase.userId,
        companyName: "Unknown company",
        eventName: purchase.eventName || purchase.eventId,
        ticketsAmount: purchase.ticketsAmount ?? purchase.ticketIds?.length ?? 0,
        totalPrice: purchase.totalPrice,
        purchaseDate: purchase.purchaseDate,
    }));
}