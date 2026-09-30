package com.sakurakugu.archweaver.config;

import com.sakurakugu.archweaver.platform.PlatformConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** 服务端假玩家规则，具体配置文件由加载器模块提供。 */
public final class ArchWeaverConfig {
    private static PlatformConfig backend = new Defaults();

    private ArchWeaverConfig() {
    }

    public static void install(PlatformConfig value) { backend = value; }

    public static boolean canUseCommands(CommandSourceStack source) {
        return switch (backend.permissionLevel()) {
            case 0 -> Commands.hasPermission(Commands.LEVEL_ALL).test(source);
            case 1 -> Commands.hasPermission(Commands.LEVEL_MODERATORS).test(source);
            case 2 -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
            case 3 -> Commands.hasPermission(Commands.LEVEL_ADMINS).test(source);
            default -> Commands.hasPermission(Commands.LEVEL_OWNERS).test(source);
        };
    }

    public static boolean allowOfflineProfiles() { return backend.allowOfflineProfiles(); }
    public static ProfileStrategy profileStrategy() { return backend.profileStrategy(); }
    public static boolean restoreFakePlayers() { return backend.restoreFakePlayers(); }
    public static int maxChunkLoadingRadius() { return backend.maxChunkLoadingRadius(); }
    public static int maxForcedChunks() { return backend.maxForcedChunks(); }
    public static int maxTickingChunks() { return backend.maxTickingChunks(); }
    public static int maxPlayerLoadingChunks() { return backend.maxPlayerLoadingChunks(); }
    public static boolean containerTransferButtons() { return backend.containerTransferButtons(); }

    /** 返回全局界面可即时调整的布尔配置快照。 */
    public static int globalSettingsMask() {
        int mask = 0;
        for (GlobalSetting setting : GlobalSetting.values()) if (setting.enabled()) mask |= 1 << setting.ordinal();
        return mask;
    }

    /** 切换一项全局配置并立即写回配置文件。 */
    public static boolean toggleGlobalSetting(int index) {
        GlobalSetting[] settings = GlobalSetting.values();
        if (index < 0 || index >= settings.length) return false;
        settings[index].toggle();
        backend.save();
        return true;
    }

    public enum GlobalSetting {
        RESTORE_FAKE_PLAYERS,
        CONTAINER_TRANSFER_BUTTONS;

        public boolean enabled() {
            return this == RESTORE_FAKE_PLAYERS
                ? ArchWeaverConfig.restoreFakePlayers() : ArchWeaverConfig.containerTransferButtons();
        }

        private void toggle() {
            if (this == RESTORE_FAKE_PLAYERS) backend.setRestoreFakePlayers(!enabled());
            else backend.setContainerTransferButtons(!enabled());
        }
    }

    public enum ProfileStrategy { ONLINE_PREFERRED, CACHE_ONLY, OFFLINE_ONLY }

    private static final class Defaults implements PlatformConfig {
        public int permissionLevel() { return 2; }
        public boolean allowOfflineProfiles() { return true; }
        public ProfileStrategy profileStrategy() { return ProfileStrategy.ONLINE_PREFERRED; }
        public boolean restoreFakePlayers() { return true; }
        public int maxChunkLoadingRadius() { return 8; }
        public int maxForcedChunks() { return 2048; }
        public int maxTickingChunks() { return 512; }
        public int maxPlayerLoadingChunks() { return 65536; }
        public boolean containerTransferButtons() { return true; }
        public void setRestoreFakePlayers(boolean value) { }
        public void setContainerTransferButtons(boolean value) { }
        public void save() { }
    }
}
