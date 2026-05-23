import api from "./api";

export type ActivePurchaseDTO = {
    activePurchaseId: string;
    userID: string;
    eventID: string;
    ticketIDs: string[];
    price: number;
    endTime: string;
};

export async function selectSittingTickets(
    eventId: string,
    ticketIDs: string[],
    userID: string,
    isConfirmedAge: boolean,
): Promise<ActivePurchaseDTO> {
    const response = await api.post(`/purchases/events/${eventId}/sitting`, {
        ticketIDs,
        userID,
        isConfirmedAge,
    });

    return response.data.data as ActivePurchaseDTO;
}

export async function selectStandingTickets(
    eventId: string,
    areaID: string,
    amount: number,
    userID: string,
    isConfirmedAge: boolean,
): Promise<ActivePurchaseDTO> {
    const response = await api.post(`/purchases/events/${eventId}/standing`, {
        areaID,
        amount,
        userID,
        isConfirmedAge,
    });

    return response.data.data as ActivePurchaseDTO;
}