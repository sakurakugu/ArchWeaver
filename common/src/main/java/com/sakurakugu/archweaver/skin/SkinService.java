package com.sakurakugu.archweaver.skin;

import com.mojang.authlib.GameProfile;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.MannequinManager;
import com.sakurakugu.archweaver.platform.PlatformNetworking;
import com.sakurakugu.archweaver.network.SkinSyncPayload;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 皮肤覆盖的服务端业务；所有修改都在服务端主线程执行。 */
public final class SkinService {
    public static final int MAX_PNG_BYTES = 128 * 1024;
    private SkinService() { }
    public static SkinSavedData data(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(SkinSavedData.TYPE); }

    public static void applyProfile(MinecraftServer server, UUID target, SkinSavedData.TargetType type, GameProfile source) {
        SkinSavedData.Record record = SkinSavedData.Record.profile(target, type, source);
        data(server).put(record);
        // 两种目标都保留原始档案；恢复时只删除覆盖，改名也不会改变基础皮肤。
        sync(server, record);
    }

    public static void applyPng(MinecraftServer server, UUID target, SkinSavedData.TargetType type, byte[] png, String model) {
        applyPng(server, target, type, png, model, "");
    }

    public static void applyPng(MinecraftServer server, UUID target, SkinSavedData.TargetType type, byte[] png, String model, String fileName) {
        // 服务端独立净化，网络请求和 API 调用都只能保存、同步纯像素 PNG。
        byte[] sanitized = SkinPngSanitizer.sanitize(png);
        SkinSavedData.Record record = SkinSavedData.Record.png(target, type, sanitized, model, fileName);
        data(server).put(record); sync(server, record);
    }

    public static void clear(MinecraftServer server, UUID target, SkinSavedData.TargetType type) {
        data(server).remove(target, type);
        // 基础档案始终不变，删除覆盖即可恢复初始皮肤。
        PlatformNetworking.sendToAllPlayers(server, removal(target, type));
    }

    /**
     * 在同一逻辑目标切换实体类型时迁移皮肤覆盖记录，并刷新客户端缓存。
     *
     * <p>玩家和玩偶的实体 UUID 不相同，转换后必须把原类型的记录改挂到新实体上，
     * 否则记录会继续留在已经不存在的目标上。</p>
     */
    public static void transfer(MinecraftServer server, UUID source, SkinSavedData.TargetType sourceType,
                                UUID target, SkinSavedData.TargetType targetType) {
        if (source.equals(target) && sourceType == targetType) return;
        SkinSavedData saved = data(server);
        SkinSavedData.Record record = saved.find(source, sourceType).orElse(null);
        if (record == null) return;
        saved.remove(source, sourceType);
        SkinSavedData.Record migrated = new SkinSavedData.Record(target, targetType, record.kind(), record.properties(),
            record.png(), record.model(), record.sourceUuid(), record.sourceName(), record.fileName());
        saved.put(migrated);
        // 先清掉旧实体的客户端缓存，再下发新实体的覆盖，避免转换期间残留旧纹理。
        PlatformNetworking.sendToAllPlayers(server, removal(source, sourceType));
        if (targetPresent(server, migrated)) PlatformNetworking.sendToAllPlayers(server, payload(migrated));
    }

    public static Optional<SkinSavedData.Record> find(MinecraftServer server, UUID target, SkinSavedData.TargetType type) { return data(server).find(target, type); }

    public static void syncTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        for (SkinSavedData.Record record : data(server).records()) {
            if (targetPresent(server, record)) PlatformNetworking.sendToPlayer(player, payload(record));
        }
    }

    /** 假人上线或下线时切换覆盖的生效状态；记录本身保留，假人回来时重新启用。 */
    public static void syncFakePresence(MinecraftServer server, UUID fakeId, boolean online) {
        SkinSavedData.Record record = data(server).find(fakeId, SkinSavedData.TargetType.PLAYER).orElse(null);
        if (record == null) return;
        PlatformNetworking.sendToAllPlayers(server, online ? payload(record) : removal(fakeId, SkinSavedData.TargetType.PLAYER));
    }

    private static void sync(MinecraftServer server, SkinSavedData.Record record) {
        // 目标不在场时只保存记录，等它上线或重新登记后再下发。
        if (targetPresent(server, record)) PlatformNetworking.sendToAllPlayers(server, payload(record));
    }

    /**
     * 覆盖只对“在场”的目标生效：假人必须是当前在线的假人实体，玩偶必须仍然登记在册。
     * 同名真人接替同一个 UUID 时不算在场，因此不会沿用假人的覆盖。
     */
    private static boolean targetPresent(MinecraftServer server, SkinSavedData.Record record) {
        return record.type() == SkinSavedData.TargetType.MANNEQUIN
            ? MannequinManager.data(server).find(record.target()).isPresent()
            : FakePlayerManager.findAny(server, record.target()) != null;
    }

    private static SkinSyncPayload payload(SkinSavedData.Record record) {
        return new SkinSyncPayload(record.target(), record.type(), record.kind(), record.properties(), record.pngBytes(), record.model(), false,
            record.sourceUuid(), record.sourceName(), record.fileName());
    }

    /** 让客户端丢弃该目标的覆盖；记录是否保留由调用方决定。 */
    private static SkinSyncPayload removal(UUID target, SkinSavedData.TargetType type) {
        return new SkinSyncPayload(target, type, SkinSavedData.Kind.PROFILE, List.of(), new byte[0], "", true, target, "", "");
    }
    public static void validatePng(byte[] bytes) {
        if (bytes == null || bytes.length < 33 || bytes.length > MAX_PNG_BYTES) throw new IllegalArgumentException("皮肤 PNG 大小非法");
        byte[] sig = {(byte)0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (!Arrays.equals(Arrays.copyOf(bytes, 8), sig) || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') throw new IllegalArgumentException("皮肤文件不是 PNG");
        if (java.nio.ByteBuffer.wrap(bytes, 8, 4).getInt() != 13) throw new IllegalArgumentException("皮肤 PNG 文件头非法");
        int width = java.nio.ByteBuffer.wrap(bytes, 16, 4).getInt(), height = java.nio.ByteBuffer.wrap(bytes, 20, 4).getInt();
        validateDimensions(width, height);
    }

    public static void validateDimensions(int width, int height) {
        if (width < 64 || width > 1024 || (width & (width - 1)) != 0 || (height != width && height != width / 2))
            throw new IllegalArgumentException("皮肤宽度必须是 64~1024 的 2 次幂，高度必须等于宽度或宽度的一半");
    }
}
