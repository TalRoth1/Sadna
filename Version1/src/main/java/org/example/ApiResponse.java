package org.example;

/**
 * Generic API response wrapper.
 *
 * Every endpoint returns this structure:
 *   { "success": boolean, "message": string, "data": T | null }
 *
 * Use the static factory methods (success / error) instead of calling the
 * constructor directly — they're more readable at call sites.
 */
public class ApiResponse<T> {
    public final boolean success;
    public final String message;
    public final T data;

    public ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
