package com.sakurakugu.archweaver.api;

/**
 * ArchWeaver 对外 API 的入口常量。
 *
 * <p>这个包是附属模组唯一应当依赖的东西。{@code common} 里的其他类都是实现细节，
 * 随时可能改签名或改语义，附属模组在编译期也看不到它们。
 *
 * <p>API 版本独立于模组版本：只有在这里的签名或语义发生破坏性变更时才递增，
 * 模组自身发补丁不会动它。附属模组可以在启动时断言自己需要的版本。
 */
public final class ArchWeaverApi {
    /** 被依赖的核心模组 ID。 */
    public static final String MOD_ID = "archweaver";

    /** 当前 API 版本，破坏性变更时递增。 */
    public static final int API_VERSION = 1;

    private ArchWeaverApi() {
    }
}
