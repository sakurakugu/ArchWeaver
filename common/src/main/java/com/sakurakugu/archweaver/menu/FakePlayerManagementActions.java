package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.entity.MannequinManager;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import net.minecraft.network.chat.Component;
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
                failure(viewer, mannequinRenameFailureKey(exception), name);
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
        completeRename(viewer, target, name);
    }

    private static void completeRename(
        ServerPlayer viewer,
        FakeServerPlayer target,
        String name
    ) {
        String oldName = target.getGameProfile().name();
        FakePlayerManager.RenameResult result = FakePlayerManager.rename(target, name);
        if (!result.successful()) {
            failure(viewer, result.messageKey(), name);
            return;
        }

        viewer.sendSystemMessage(Component.translatable(
            "commands.archweaver.fakeplayer.renamed", oldName, name));
        FakePlayerMenuOpener.openInventory(viewer, result.player());
    }

    /** 玩偶改名把冲突来源写在异常消息里，这里翻译成对应的提示文案。 */
    private static String mannequinRenameFailureKey(IllegalArgumentException exception) {
        return switch (String.valueOf(exception.getMessage())) {
            case "invalid_name" -> "commands.archweaver.fakeplayer.invalid_name";
            case "mannequin_duplicate" -> "commands.archweaver.fakeplayer.mannequin_duplicate";
            case "registered_duplicate" -> "commands.archweaver.fakeplayer.registered_duplicate";
            default -> "commands.archweaver.fakeplayer.duplicate";
        };
    }

    private static void failure(ServerPlayer viewer, String key, Object... arguments) {
        viewer.sendSystemMessage(Component.translatable(key, arguments).withColor(0xFF5555));
    }
}
