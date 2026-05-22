import axios from "axios";

type ApiResponse<T> = {
    success: boolean;
    message: string;
    data: T;
};

export async function registerToLottery(
    eventId: string,
    memberId: string,
    ticketAmount: number,
): Promise<void> {
    const response = await axios.post<ApiResponse<null>>(
        `/api/purchases/events/${eventId}/lottery/register`,
        {
            memberId,
            ticketAmount,
        },
    );

    if (!response.data.success) {
        throw new Error(response.data.message);
    }
}