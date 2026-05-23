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
    buyerName: string;
    companyName: string;
    eventName: string;
    ticketsAmount: number;
    totalPrice: number;
    purchaseDate: string;
};

export type SystemAnalytics = {
    registeredUsersCount: number;
    loggedInUsersCount: number;
    activeCompaniesCount: number;
    activeQueuesCount: number;
    activePurchasesCount: number;
    totalPurchasesCount: number;
    createdAt: string;
};

export type QueueInfo = {
    id: string;
    eventName: string;
    waitingUsers: number;
    flowRatePerMinute: number;
    activeSelectorsCount: number;
    status: "active" | "cleared";
};