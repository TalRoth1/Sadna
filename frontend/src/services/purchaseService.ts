import api from "./api";

// =============================================================================
// REAL BACKEND CALLS — Ticket Purchase flow
//
// PurchaseController exposes the following endpoints, all returning the
// standard `{ success, message, data }` envelope:
//
//   POST   /api/purchases/events/{eventId}/sitting           (SelectSittingRequest)   -> ActivePurchaseDTO
//   POST   /api/purchases/events/{eventId}/standing          (SelectStandingRequest)  -> ActivePurchaseDTO
//   PUT    /api/purchases/active/{activePurchaseId}/sitting  (UpdateSittingRequest)
//   PUT    /api/purchases/active/{activePurchaseId}/standing (UpdateStandingRequest)
//   DELETE /api/purchases/active/{activePurchaseId}
//   POST   /api/purchases/active/{activePurchaseId}/complete (CompletePurchaseRequest)
//   POST   /api/purchases/events/{eventId}/lottery/register  (LotteryRegisterRequest)
//
// We follow the same pattern as companyService.ts / eventSearchService.ts:
//   - use the shared `api` axios client (baseURL ends in /api, attaches token)
//   - keep request field names identical to the backend DTOs
//   - unwrap response.data.data so callers receive only the payload
//   - surface backend error messages by re-throwing axios errors with the
//     server-supplied `message` so the UI can display them verbatim
// =============================================================================

/**
 * Wire format returned by PurchaseService.toActivePurchaseDTO. The frontend
 * timer pins itself to `endTime` so its countdown stays in lockstep with
 * `ActivePurchaseCleaner` on the server.
 */
export type ActivePurchaseResponse = {
    activePurchaseId: string;
    userId: string;
    eventId: string;
    ticketPrices: Record<string, number>;
    endTime: string;
    isGuestConfirmedAge: boolean;
    coupon: string | null;
    price: number;
    maxWaitTime: number;
    lastUpdate: string;
    eventName: string | null;
    eventDate: string | null;
    eventLocation: string | null;
    ticketsAmount: number;
};

type SelectSittingRequestBody = {
    ticketIDs: string[];
    userID: string;
    isConfirmedAge: boolean;
    accessCode?: string | null;
};

type SelectStandingRequestBody = {
    amount: number;
    areaID: string;
    userID: string;
    isConfirmedAge: boolean;
    accessCode?: string | null;
};

type UpdateSittingRequestBody = {
    newTicketIds: string[];
};

type UpdateStandingRequestBody = {
    newAmount: number;
    areaId: string;
};

// Mirrors the backend org.example.ApplicationLayer.PaymentDetails fields,
// which are forwarded as-is to the external WSEP payment gateway
// (action_type=pay: card_number, month, year, holder, cvv, id, currency).
export type PaymentDetails = {
    currency?: string;
    cardNumber: string;
    month: string;
    year: string;
    holder: string;
    cvv: string;
    id: string;
};

type CompletePurchaseRequestBody = {
    paymentDetails: PaymentDetails;
    couponCode: string | null;
};

type LotteryRegisterRequestBody = {
    memberId: string;
    ticketAmount: number;
};

/**
 * Translate axios errors into a thrown `Error` whose `.message` is the
 * server-supplied human-readable string (the backend wraps every failure
 * as `{ success: false, message: "...", data: null }` with HTTP 400/404).
 * Falls back to the network-level message otherwise.
 */
function extractMessage(error: unknown, fallback: string): string {
    if (
        typeof error === "object" &&
        error !== null &&
        "response" in error
    ) {
        const response = (error as { response?: { data?: { message?: string } } })
            .response;
        const message = response?.data?.message;
        if (typeof message === "string" && message.length > 0) {
            return message;
        }
    }
    if (error instanceof Error && error.message) {
        return error.message;
    }
    return fallback;
}

// -----------------------------------------------------------------------------
// 1. Ticket selection — creates an ActivePurchase server-side and returns
//    the canonical endTime the timer should sync to.
// -----------------------------------------------------------------------------

export async function getActivePurchasesForUser(
    userId: string,
): Promise<ActivePurchaseResponse[]> {
    try {
        const response = await api.get(
            `/purchases/users/${encodeURIComponent(userId)}/active`,
        );

        return (response.data.data ?? []) as ActivePurchaseResponse[];
    } catch (error) {
        throw new Error(
            extractMessage(error, "Failed to fetch active purchases."),
            { cause: error },
        );
    }
}


export async function selectSittingTickets(
    eventId: string,
    ticketIds: string[],
    userId: string,
    isConfirmedAge: boolean,
    accessCode?: string | null,
): Promise<ActivePurchaseResponse> {
    const body: SelectSittingRequestBody = {
        ticketIDs: ticketIds,
        userID: userId,
        isConfirmedAge,
        accessCode: accessCode ?? null,
    };
    try {
        const response = await api.post(
            `/purchases/events/${encodeURIComponent(eventId)}/sitting`,
            body,
        );
        return response.data.data as ActivePurchaseResponse;
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to reserve sitting tickets."), {
            cause: error,
        });
    }
}

export async function selectStandingTickets(
    eventId: string,
    areaId: string,
    amount: number,
    userId: string,
    isConfirmedAge: boolean,
    accessCode?: string | null,
): Promise<ActivePurchaseResponse> {
    const body: SelectStandingRequestBody = {
        amount,
        areaID: areaId,
        userID: userId,
        isConfirmedAge,
        accessCode: accessCode ?? null,
    };
    try {
        const response = await api.post(
            `/purchases/events/${encodeURIComponent(eventId)}/standing`,
            body,
        );
        return response.data.data as ActivePurchaseResponse;
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to reserve standing tickets."), {
            cause: error,
        });
    }
}

// -----------------------------------------------------------------------------
// 1b. Resume lookup — the TicketPurchase page calls this on mount so it can
//     restore the user's in-flight reservation (selected tickets + the exact
//     server-side endTime) after a route change or full reload. Returns
//     `null` when the user has nothing reserved for this event, or when the
//     reservation has already expired.
// -----------------------------------------------------------------------------

export async function getActivePurchaseForEvent(
    eventId: string,
    userId: string,
): Promise<ActivePurchaseResponse | null> {
    try {
        const response = await api.get(
            `/purchases/events/${encodeURIComponent(eventId)}/active`,
            { params: { userId } },
        );
        const data = response.data?.data;
        return (data ?? null) as ActivePurchaseResponse | null;
    } catch (error) {
        throw new Error(
            extractMessage(error, "Failed to fetch active purchase."),
            { cause: error },
        );
    }
}

// -----------------------------------------------------------------------------
// 2. Active purchase management — the backend keeps the original endTime
//    pinned across these updates, so the timer doesn't reset.
// -----------------------------------------------------------------------------

export async function updateSittingTickets(
    activePurchaseId: string,
    newTicketIds: string[],
): Promise<void> {
    const body: UpdateSittingRequestBody = { newTicketIds };
    try {
        await api.put(
            `/purchases/active/${encodeURIComponent(activePurchaseId)}/sitting`,
            body,
        );
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to update sitting tickets."), {
            cause: error,
        });
    }
}

export async function updateStandingTickets(
    activePurchaseId: string,
    areaId: string,
    newAmount: number,
): Promise<void> {
    const body: UpdateStandingRequestBody = { newAmount, areaId };
    try {
        await api.put(
            `/purchases/active/${encodeURIComponent(activePurchaseId)}/standing`,
            body,
        );
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to update standing tickets."), {
            cause: error,
        });
    }
}

export async function cancelActivePurchase(
    activePurchaseId: string,
): Promise<void> {
    try {
        await api.delete(
            `/purchases/active/${encodeURIComponent(activePurchaseId)}`,
        );
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to cancel reservation."), {
            cause: error,
        });
    }
}

// -----------------------------------------------------------------------------
// 3. Checkout — completes the purchase per the event's purchase + discount
//    policies. The backend rolls back released tickets on any failure
//    ("all or nothing", per spec §II.2.8).
// -----------------------------------------------------------------------------

export async function completePurchase(
    activePurchaseId: string,
    paymentDetails: PaymentDetails,
    couponCode: string | null,
): Promise<string[]> {
    const body: CompletePurchaseRequestBody = {
        paymentDetails,
        couponCode,
    };

    try {
        const response = await api.post(
            `/purchases/active/${encodeURIComponent(activePurchaseId)}/complete`,
            body,
        );

        // Envelope is { success, message, data: { issuedTicketRef, issuedTicketRefs } }.
        const data = response.data?.data;

        if (Array.isArray(data?.issuedTicketRefs)) {
            return data.issuedTicketRefs;
        }

        if (data?.issuedTicketRef) {
            return [data.issuedTicketRef];
        }

        return [];
    } catch (error) {
        throw new Error(extractMessage(error, "Payment failed. Please try again."), {
            cause: error,
        });
    }
}

// -----------------------------------------------------------------------------
// 4. Lottery — spec §II.3.6: members only. The backend enforces this
//    (rejects guests and logged-out members with a clear message), so the
//    error path already carries the right text for the UI.
// -----------------------------------------------------------------------------

export async function registerToLottery(
    eventId: string,
    memberId: string,
    ticketAmount: number,
): Promise<void> {
    const body: LotteryRegisterRequestBody = { memberId, ticketAmount };
    try {
        await api.post(
            `/purchases/events/${encodeURIComponent(eventId)}/lottery/register`,
            body,
        );
    } catch (error) {
        throw new Error(extractMessage(error, "Failed to register for the lottery."), {
            cause: error,
        });
    }
}
