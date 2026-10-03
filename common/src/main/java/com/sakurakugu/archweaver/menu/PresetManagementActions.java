package com.sakurakugu.archweaver.menu;

import com.sakurakugu.archweaver.network.PresetActionPayload;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import com.sakurakugu.archweaver.preset.PresetService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 在服务端执行预设管理界面的操作并返回最新快照。 */
public final class PresetManagementActions {
    private PresetManagementActions() {
    }

    public static void handle(ServerPlayer viewer, PresetActionPayload payload) {
        boolean groupsPage = switch (payload.action()) {
            case SAVE_PRESET, LOAD_PRESET, REMOVE_PRESET -> false;
            default -> true;
        };
        switch (payload.action()) {
            case SAVE_PRESET -> savePreset(viewer, payload.first(), payload.second(), payload.third());
            case LOAD_PRESET -> loadPreset(viewer, payload.first());
            case REMOVE_PRESET -> removePreset(viewer, payload.first());
            case CREATE_GROUP -> createGroup(viewer, payload.first());
            case ADD_TO_GROUP -> addToGroup(viewer, payload.first(), payload.second());
            case REMOVE_FROM_GROUP -> removeFromGroup(viewer, payload.first(), payload.second());
            case LOAD_GROUP -> loadGroup(viewer, payload.first(), false);
            case UNLOAD_GROUP -> loadGroup(viewer, payload.first(), true);
            case REMOVE_GROUP -> removeGroup(viewer, payload.first());
        }
        FakePlayerMenuOpener.openPresetManagement(viewer, groupsPage);
    }

    private static void savePreset(ServerPlayer viewer, String id, String playerName, String description) {
        report(viewer, PresetService.savePreset(viewer.level().getServer(), id, playerName, description),
            "commands.fakeplayer.preset.preset_saved", id, playerName);
    }

    private static void loadPreset(ServerPlayer viewer, String id) {
        var preset = FakePlayerPersistence.data(viewer.level().getServer())
            .preset(id).orElse(null);
        var result = PresetService.loadPreset(viewer.level().getServer(), id);
        if (!result.successful()) {
            failure(viewer, result.failureKey(), result.failureArguments());
            return;
        }
        success(viewer, "commands.fakeplayer.preset.preset_loaded", id, preset.player().name());
    }

    private static void removePreset(ServerPlayer viewer, String id) {
        report(viewer, PresetService.removePreset(viewer.level().getServer(), id),
            "commands.fakeplayer.preset.preset_removed", id);
    }

    private static void createGroup(ServerPlayer viewer, String id) {
        report(viewer, PresetService.createGroup(viewer.level().getServer(), id),
            "commands.fakeplayer.preset.group_created", id);
    }

    private static void addToGroup(ServerPlayer viewer, String groupId, String presetId) {
        report(viewer, PresetService.addToGroup(viewer.level().getServer(), groupId, presetId),
            "commands.fakeplayer.preset.group_member_added", presetId, groupId);
    }

    private static void removeFromGroup(ServerPlayer viewer, String groupId, String presetId) {
        report(viewer, PresetService.removeFromGroup(viewer.level().getServer(), groupId, presetId),
            "commands.fakeplayer.preset.group_member_removed", presetId, groupId);
    }

    private static void loadGroup(ServerPlayer viewer, String id, boolean unload) {
        PresetService.GroupLoadResult result = PresetService.loadGroup(viewer.level().getServer(), id, unload);
        if (!result.groupFound()) {
            failure(viewer, "commands.fakeplayer.preset.group_not_found", id);
            return;
        }
        success(viewer, unload ? "commands.fakeplayer.preset.group_unloaded" : "commands.fakeplayer.preset.group_loaded",
            id, result.succeeded(), result.failed());
    }

    private static void removeGroup(ServerPlayer viewer, String id) {
        report(viewer, PresetService.removeGroup(viewer.level().getServer(), id),
            "commands.fakeplayer.preset.group_removed", id);
    }

    private static void report(ServerPlayer viewer, PresetService.Result result, String successKey, Object... successArguments) {
        if (result.successful()) {
            success(viewer, successKey, successArguments);
        } else {
            failure(viewer, result.failureKey(), result.failureArguments());
        }
    }

    private static void success(ServerPlayer viewer, String key, Object... arguments) {
        viewer.sendSystemMessage(Component.translatable(key, arguments));
    }

    private static void failure(ServerPlayer viewer, String key, Object... arguments) {
        viewer.sendSystemMessage(Component.translatable(key, arguments).withColor(0xFF5555));
    }
}
