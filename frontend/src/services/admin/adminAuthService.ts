import api from "../api";

export async function verifyPlatformAdmin(_userId: string): Promise<boolean> {
    try {
        await api.get("/admin/dashboard");
        return true;
    } catch {
        return false;
    }
}