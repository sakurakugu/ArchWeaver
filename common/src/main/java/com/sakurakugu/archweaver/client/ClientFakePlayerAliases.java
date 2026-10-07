package com.sakurakugu.archweaver.client;

import com.sakurakugu.archweaver.network.FakePlayerAliasPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;

/** 按档案 UUID 缓存别名，跨维度和实体重生仍可正确显示。 */
public final class ClientFakePlayerAliases {
    private static final Map<UUID, FakePlayerAliasPayload> ALIASES = new HashMap<>();

    private ClientFakePlayerAliases() {
    }

    public static void accept(FakePlayerAliasPayload payload) {
        // 别名为空但需要显示「假人」占位时同样保留，只有明确清除的包才移除。
        if (payload.alias().isEmpty() && !payload.emptyAliasMarker()) ALIASES.remove(payload.playerId());
        else ALIASES.put(payload.playerId(), payload);
    }

    public static FakePlayerAliasPayload nameTag(AbstractClientPlayer player) {
        return ALIASES.get(ClientPossession.displayedPlayerId(player));
    }

    public static void clear() {
        ALIASES.clear();
    }
}
