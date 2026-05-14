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
    status: "open" | "answered";
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
    status: "active" | "cleared";
};