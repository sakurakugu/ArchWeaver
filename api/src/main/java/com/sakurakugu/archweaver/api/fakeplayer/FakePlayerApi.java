package com.sakurakugu.archweaver.api.fakeplayer;

import com.sakurakugu.archweaver.api.ApiResult;
import com.sakurakugu.archweaver.entity.FakePlayerManager;
import com.sakurakugu.archweaver.entity.FakeServerPlayer;
import com.sakurakugu.archweaver.entity.ProfileResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 假人 API。
 *
 * <p>除 {@link #spawn} 外的方法都必须在服务端主线程调用。
 */
public final class FakePlayerApi {
    private FakePlayerApi() {
    }

    /**
     * 生成假人。
     *
     * <p>档案解析要查缓存或联网，所以是异步的。返回的 future 在任意线程完成，
     * 回调里要碰游戏状态必须自己切回主线程（{@code server.execute(...)}）。
     *
     * <p>重名、档案解析失败都会以失败的 {@link ApiResult} 返回，不抛异常。
     */
    public static CompletableFuture<ApiResult> spawn(MinecraftServer server, SpawnSpec spec) {
        return ProfileResolver.resolve(server, spec.name()).thenApply(resolved -> {
            if (!resolved.successful()) {
                return ApiResult.failure("无法解析玩家档案：" + resolved.status());
            }
            try {
                FakePlayerManager.spawn(server, spec.level(), resolved.profile(), spec.position(),
                    spec.rotation(), spec.gameType(), spec.flying());
                return ApiResult.success();
            } catch (RuntimeException exception) {
                return ApiResult.failure(exception.getMessage());
            }
        });
    }

    /** 移除假人并清除其驻留记录。目标不存在时失败。 */
    public static ApiResult remove(MinecraftServer server, UUID id) {
        FakeServerPlayer fake = FakePlayerManager.all(server).stream()
            .filter(value -> value.getUUID().equals(id)).findFirst().orElse(null);
        if (fake == null) {
            return ApiResult.failure("找不到指定假人");
        }
        FakePlayerManager.remove(fake);
        return ApiResult.success();
    }

    public static Optional<FakePlayerInfo> find(MinecraftServer server, String name) {
        return Optional.ofNullable(FakePlayerManager.find(server, name)).map(FakePlayerApi::convert);
    }

    /** 当前在线的全部假人。 */
    public static List<FakePlayerInfo> all(MinecraftServer server) {
        return FakePlayerManager.all(server).stream().map(FakePlayerApi::convert).toList();
    }

    /** 判断一个玩家是不是本模组的假人。 */
    public static boolean isFakePlayer(ServerPlayer player) {
        return player instanceof FakeServerPlayer;
    }

    private static FakePlayerInfo convert(FakeServerPlayer fake) {
        return new FakePlayerInfo(fake.getUUID(), fake.getGameProfile().name(),
            fake.level().dimension().identifier(), fake.position());
    }
}
