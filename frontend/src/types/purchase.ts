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
    issuedTicketRef?: string;
};