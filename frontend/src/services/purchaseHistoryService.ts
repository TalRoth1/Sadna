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
    issuedTicketRef?: string;
    // Backend may already send a parsed list; we fall back to splitting the
    // comma-joined `issuedTicketRef` when it doesn't.
    issuedTicketRefs?: string[];
    // Per-ticket seat descriptor, aligned by index with the issued ticket refs.
    seatLabels?: string[];
};

/**
 * The backend persists every per-ticket code in a single `issued_ticket_ref`
 * column, joined by ",". Split it back into one code per ticket so the UI can
 * render a distinct QR for each. Tolerates the list form too.
 */
function parseTicketRefs(dto: PurchaseHistoryDto): string[] {
    if (dto.issuedTicketRefs && dto.issuedTicketRefs.length > 0) {
        return dto.issuedTicketRefs.map((ref) => ref.trim()).filter(Boolean);
    }

    if (!dto.issuedTicketRef) {
        return [];
    }

    return dto.issuedTicketRef
        .split(",")
        .map((ref) => ref.trim())
        .filter(Boolean);
}

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
        issuedTicketRef: dto.issuedTicketRef,
        issuedTicketRefs: parseTicketRefs(dto),
        seatLabels: dto.seatLabels ?? [],
    };
}