package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.entity.MannequinManager;
import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.ProfileResolver;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 处理假人详情页中需要字符串参数的服务端操作。 */
public final class FakePlayerManagementActions {
    private FakePlayerManagementActions() {
    }

    public static void setAlias(ServerPlayer viewer, String alias) {
        if (viewer.containerMenu instanceof MannequinInventoryMenu menu && menu.stillValid(viewer)) {
            try {
                MannequinManager.setAlias(viewer.level().getServer(), menu.mannequinId(), alias);
                menu.broadcastChanges();
            } catch (IllegalArgumentException exception) {
                failure(viewer, "commands.archweaver.fakeplayer.invalid_alias");
            }
            return;
        }
        if (!(viewer.containerMenu instanceof FakePlayerInventoryMenu menu)
            || menu.view() != FakePlayerInventoryMenu.View.INVENTORY
            || !menu.canManageTarget(viewer)) {
            return;
        }
        try {
            menu.target().setAlias(alias);
            FakePlayerMenuOpener.openInventory(viewer, menu.target());
        } catch (IllegalArgumentException exception) {
            failure(viewer, "commands.archweaver.fakeplayer.invalid_alias");
        }
    }

    public static void rename(ServerPlayer viewer, String name) {
        if (viewer.containerMenu instanceof MannequinInventoryMenu menu && menu.stillValid(viewer)) {
            try {
                MannequinManager.rename(viewer.level().getServer(), menu.mannequinId(), name);
                menu.broadcastChanges();
            } catch (IllegalArgumentException exception) {
                failure(viewer, "invalid_name".equals(exception.getMessage())
                    ? "commands.archweaver.fakeplayer.invalid_name" : "commands.archweaver.fakeplayer.duplicate", name);
            }
            return;
        }
        if (!(viewer.containerMenu instanceof FakePlayerInventoryMenu menu)
            || menu.view() != FakePlayerInventoryMenu.View.INVENTORY
            || !menu.canManageTarget(viewer)) {
            return;
        }
        if (!name.matches("[A-Za-z0-9_-]{1,16}")) {
            viewer.sendSystemMessage(Component.translatable("commands.archweaver.fakeplayer.invalid_name").withColor(0xFF5555));
            return;
        }

        FakeServerPlayer target = menu.target();
        if (target.getGameProfile().name().equals(name)) {
            return;
        }
        viewer.sendSystemMessage(Component.translatable("commands.archweaver.fakeplayer.resolving_profile", name));
        MinecraftServer server = viewer.level().getServer();
        ProfileResolver.resolve(server, name).whenCompleteAsync((profileResult, throwable) -> {
            if (throwable != null) {
                ArchWeaverMod.LOGGER.error("解析假玩家新名称 {} 的档案时发生异常", name, throwable);
                failure(viewer, "commands.archweaver.fakeplayer.profile_service_unavailable", name);
                return;
            }
            if (!profileResult.successful()) {
                failure(viewer, profileFailureKey(profileResult.status()), name);
                return;
            }
            if (target.hasDisconnected()) {
                failure(viewer, "commands.archweaver.fakeplayer.not_found", target.getGameProfile().name());
                return;
            }
            completeRename(viewer, target, profileResult.profile());
        }, server);
    }

    private static void completeRename(
        ServerPlayer viewer,
        FakeServerPlayer target,
        com.mojang.authlib.GameProfile profile
    ) {
        String oldName = target.getGameProfile().name();
        FakePlayerManager.RenameResult result = FakePlayerManager.rename(target, profile);
        if (!result.successful()) {
            failure(viewer, result.messageKey(), profile.name());
            return;
        }

        viewer.sendSystemMessage(Component.translatable(
            "commands.archweaver.fakeplayer.renamed", oldName, profile.name()));
        FakePlayerMenuOpener.openInventory(viewer, result.player());
    }

    private static String profileFailureKey(ProfileResolver.Status status) {
        return switch (status) {
            case BUSY -> "commands.archweaver.fakeplayer.profile_busy";
            case SERVICE_UNAVAILABLE -> "commands.archweaver.fakeplayer.profile_service_unavailable";
            default -> "commands.archweaver.fakeplayer.profile_not_found";
        };
    }

    private static void failure(ServerPlayer viewer, String key, Object... arguments) {
        viewer.sendSystemMessage(Component.translatable(key, arguments).withColor(0xFF5555));
    }
}
