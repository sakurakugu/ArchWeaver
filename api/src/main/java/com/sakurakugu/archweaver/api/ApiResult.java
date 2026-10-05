package com.sakurakugu.archweaver.api;

/**
 * API 调用结果。
 *
 * <p>失败原因是给人看的中文说明，不是稳定的错误码，不要拿来做分支判断。
 */
public record ApiResult(boolean successful, String reason) {
    private static final ApiResult SUCCESS = new ApiResult(true, "");

    public static ApiResult success() {
        return SUCCESS;
    }

    public static ApiResult failure(String reason) {
        return new ApiResult(false, reason == null || reason.isBlank() ? "未知错误" : reason);
    }
}
