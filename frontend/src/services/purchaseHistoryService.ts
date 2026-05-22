import api from "./api";
import type { PurchaseHistoryItem } from "../types/purchase";

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

export async function getPurchaseHistory(
    memberId: string,
): Promise<PurchaseHistoryItem[]> {
    const response = await api.get(`/purchases/history/members/${memberId}`);
    const purchases = response.data.data as PurchaseHistoryDto[];

    return purchases.map(mapPurchaseHistoryDto);
}

function mapPurchaseHistoryDto(
    dto: PurchaseHistoryDto,
    index: number,
): PurchaseHistoryItem {
    return {
        id: `${dto.eventId}-${dto.purchaseDate}-${index}`,
        userId: dto.userId,
        eventId: dto.eventId,
        ticketIds: dto.ticketIds ?? [],
        eventName: dto.eventName,
        eventDate: dto.eventDate,
        eventLocation: dto.eventLocation,
        ticketsAmount: dto.ticketsAmount ?? dto.ticketIds?.length ?? 0,
        totalPrice: dto.totalPrice,
        paymentInfo: dto.paymentInfo,
        purchaseDate: dto.purchaseDate,
    };
}