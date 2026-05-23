export type NotificationType =
    | "PURCHASE_COMPLETED"
    | "EVENT_CHANGED"
    | "EVENT_CANCELLED"
    | "SOLD_OUT"
    | "COMPANY_CLOSED"
    | "ROLE_CHANGED"
    | "ACTIVE_PURCHASE_EXPIRING"
    | "ADMIN_MESSAGE"
    | "LOTTERY_WON"
    | "QUEUE_WAITING"
    | "QUEUE_POSITION_UPDATE"
    | "QUEUE_ACCESS_GRANTED"
    | "GENERAL";

export type UserNotification = {
    id: string;
    title: string;
    message: string;
    createdAt: string;
    isRead: boolean;

    type?: NotificationType;
    targetUrl?: string | null;

    eventId?: string;
    queuePosition?: number;
    queueSize?: number;
};