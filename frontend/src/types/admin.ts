export type AdminActionId =
    | "companies"
    | "subscribers"
    | "complaints"
    | "purchases"
    | "analytics"
    | "queues";

export type AdminAction = {
    id: AdminActionId;
    title: string;
    description: string;
};

export type ProductionCompany = {
    id: string;
    name: string;
    status: "active" | "closed";
    ownersCount: number;
    managersCount: number;
};

export type Subscriber = {
    id: string;
    username: string;
    email: string;
    status: "active" | "removed";
};

export type Complaint = {
    id: string;
    title: string;
    message: string;
    reporterName: string;
    status: "open" | "answered" | "closed";
    adminResponse?: string;
    responderAdminUsername?: string;
    createdAt?: string;
    respondedAt?: string;
};

export type GlobalPurchaseRecord = {
    id: string;
    buyerId: string;
    buyerName: string;
    companyId?: string;
    companyName: string;
    eventId: string;
    eventName: string;
    eventDate?: string;
    eventLocation?: string;
    ticketIds: string[];
    ticketsAmount: number;
    paymentInfo?: string;
    totalPrice: number;
    purchaseDate: string;
};

export type AdminPurchaseFilterType = "all" | "user" | "event" | "company";;

export type SystemAnalytics = {
    // raw backend counts (kept for potential future use)
    registeredUsersCount: number;
    loggedInUsersCount: number;
    activeCompaniesCount: number;
    activeQueuesCount: number;
    activePurchasesCount: number;
    totalPurchasesCount: number;
    createdAt: string;
    // dashboard display fields
    activeVisitors: number;
    newSubscribersRate: number;
    ticketReservationRate: number;
    ticketPurchaseRate: number;
    activeQueues: number;
};

export type QueueInfo = {
    id: string;
    eventName: string;
    waitingUsers: number;
    flowRatePerMinute: number;
    activeSelectorsCount: number;
    status: "active" | "cleared";
};