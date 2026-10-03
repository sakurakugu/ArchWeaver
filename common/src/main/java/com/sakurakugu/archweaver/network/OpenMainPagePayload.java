package com.sakurakugu.archweaver.network;

import com.sakurakugu.archweaver.ArchWeaverMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端让客户端打开控制中心主页面，并指定进入时显示的页面。
 *
 * <p>主页面由客户端按快照渲染，服务端命令无法直接构造，
 * 因此只在这里发一个通知，由客户端回调 {@code ClientChunkLoadingState.openMainScreen}
 * 决定是立刻打开还是先补一次快照请求。
 *
 * <p>这里的页面枚举刻意不直接复用客户端的 {@code MainPageScreen.View}：
 * 本类在专用服务端也会被加载，不能引用只存在于客户端的界面类型。
 */
public record OpenMainPagePayload(View view) implements CustomPacketPayload {
    public static final Type<OpenMainPagePayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(ArchWeaverMod.MOD_ID, "open_main_page")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMainPagePayload> STREAM_CODEC =
        CustomPacketPayload.codec(OpenMainPagePayload::write, OpenMainPagePayload::new);

    public OpenMainPagePayload() {
        this(View.FAKE_PLAYERS);
    }

    private OpenMainPagePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readEnum(View.class));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(view);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum View {
        FAKE_PLAYERS, // 假人列表页面。
        MAP // 区块地图页面。
    }
}
