export type PurchaseHistoryItem = {
    id: string;
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
    // Raw confirmation as stored by the backend (one or more codes joined by ",").
    issuedTicketRef?: string;
    // Parsed per-ticket codes — one entry per purchased ticket, each gets its own QR.
    issuedTicketRefs: string[];
    // Per-ticket seat descriptor aligned by index with issuedTicketRefs
    // (e.g. "Sitting area 1 · Row 2 · Seat 5"). Empty when unavailable.
    seatLabels: string[];
};