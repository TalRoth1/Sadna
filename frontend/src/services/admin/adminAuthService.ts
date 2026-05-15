const mockAdminUserIds = ["user-1"];

// TODO: Replace this mock implementation with a real server authorization check once communication is implemented.
// The server must verify that the provided userId belongs to a platform admin before allowing any admin operation.
export async function verifyPlatformAdmin(userId: string): Promise<boolean> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockAdminUserIds.includes(userId));
        }, 250);
    });
}