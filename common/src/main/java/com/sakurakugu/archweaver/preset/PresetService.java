package com.sakurakugu.archweaver.preset;

import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakePlayerPossession;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.persistence.FakePlayerPersistence;
import com.sakurakugu.archweaver.persistence.FakePlayerSavedData;
import com.sakurakugu.archweaver.persistence.FakePlayerSavedData.Group;
import com.sakurakugu.archweaver.persistence.FakePlayerSavedData.PlayerSnapshot;
import com.sakurakugu.archweaver.persistence.FakePlayerSavedData.Preset;
import net.minecraft.server.MinecraftServer;

/** 预设和预设分组的共享业务服务，供命令与 GUI 入口复用。 */
public final class PresetService {
    private static final int MAX_ID_LENGTH = 64;

    private PresetService() {
    }

    public static Result savePreset(MinecraftServer server, String id, String playerName, String description) {
        if (!isValidId(id)) {
            return Result.failure("gui.archweaver.preset.invalid_id");
        }
        FakeServerPlayer fake = FakePlayerManager.find(server, playerName);
        if (fake == null) {
            return Result.failure("commands.archweaver.fakeplayer.not_found", playerName);
        }
        if (FakePlayerPossession.isPossessed(fake)) {
            return Result.failure("gui.archweaver.fakeplayer.possess_locked");
        }
        data(server).putPreset(new Preset(id, description, PlayerSnapshot.from(fake, true)));
        return Result.success();
    }

    public static Result loadPreset(MinecraftServer server, String id) {
        Preset preset = data(server).preset(id).orElse(null);
        if (preset == null) {
            return Result.failure("commands.archweaver.preset.preset_not_found", id);
        }
        FakePlayerPersistence.LoadResult result = FakePlayerPersistence.loadPreset(server, preset);
        return result.successful()
            ? Result.success()
            : Result.failure("commands.archweaver.preset.load_failed", id, result.reason());
    }

    public static Result removePreset(MinecraftServer server, String id) {
        return data(server).removePreset(id)
            ? Result.success()
            : Result.failure("commands.archweaver.preset.preset_not_found", id);
    }

    public static Result createGroup(MinecraftServer server, String id) {
        if (!isValidId(id)) {
            return Result.failure("gui.archweaver.preset.invalid_id");
        }
        return data(server).createGroup(id)
            ? Result.success()
            : Result.failure("commands.archweaver.preset.group_exists", id);
    }

    public static Result addToGroup(MinecraftServer server, String groupId, String presetId) {
        FakePlayerSavedData data = data(server);
        if (data.group(groupId).isEmpty()) {
            return Result.failure("commands.archweaver.preset.group_not_found", groupId);
        }
        if (data.preset(presetId).isEmpty()) {
            return Result.failure("commands.archweaver.preset.preset_not_found", presetId);
        }
        return data.addToGroup(groupId, presetId)
            ? Result.success()
            : Result.failure("commands.archweaver.preset.group_member_exists", presetId, groupId);
    }

    public static Result removeFromGroup(MinecraftServer server, String groupId, String presetId) {
        FakePlayerSavedData data = data(server);
        if (data.group(groupId).isEmpty()) {
            return Result.failure("commands.archweaver.preset.group_not_found", groupId);
        }
        return data.removeFromGroup(groupId, presetId)
            ? Result.success()
            : Result.failure("commands.archweaver.preset.group_member_not_found", presetId, groupId);
    }

    public static Result removeGroup(MinecraftServer server, String id) {
        return data(server).removeGroup(id)
            ? Result.success()
            : Result.failure("commands.archweaver.preset.group_not_found", id);
    }

    public static GroupLoadResult loadGroup(MinecraftServer server, String id, boolean unload) {
        FakePlayerSavedData data = data(server);
        Group group = data.group(id).orElse(null);
        if (group == null) {
            return GroupLoadResult.missing(id);
        }
        int succeeded = 0;
        int failed = 0;
        for (String presetId : group.presetIds()) {
            Preset preset = data.preset(presetId).orElse(null);
            if (preset == null) {
                failed++;
                continue;
            }
            if (unload) {
                FakeServerPlayer fake = FakePlayerManager.find(server, preset.player().name());
                if (fake != null && fake.getUUID().equals(preset.player().uuid())
                    && !FakePlayerPossession.isPossessed(fake)) {
                    FakePlayerManager.kill(fake);
                    succeeded++;
                } else {
                    failed++;
                }
            } else if (FakePlayerPersistence.loadPreset(server, preset).successful()) {
                succeeded++;
            } else {
                failed++;
            }
        }
        return GroupLoadResult.success(id, succeeded, failed);
    }

    public static boolean isValidId(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_ID_LENGTH
            && value.chars().noneMatch(Character::isWhitespace);
    }

    private static FakePlayerSavedData data(MinecraftServer server) {
        return FakePlayerPersistence.data(server);
    }

    public record Result(boolean successful, String failureKey, Object[] failureArguments) {
        public static Result success() {
            return new Result(true, "", new Object[0]);
        }

        public static Result failure(String key, Object... arguments) {
            return new Result(false, key, arguments);
        }
    }

    public record GroupLoadResult(boolean groupFound, String groupId, int succeeded, int failed) {
        private static GroupLoadResult missing(String groupId) {
            return new GroupLoadResult(false, groupId, 0, 0);
        }

        private static GroupLoadResult success(String groupId, int succeeded, int failed) {
            return new GroupLoadResult(true, groupId, succeeded, failed);
        }
    }
}
