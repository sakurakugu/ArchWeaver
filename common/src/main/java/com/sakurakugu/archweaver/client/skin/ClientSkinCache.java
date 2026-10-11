package com.sakurakugu.archweaver.client.skin;

import com.google.common.collect.ArrayListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.sakurakugu.archweaver.ArchWeaverMod;
import com.sakurakugu.archweaver.network.SkinSyncPayload;
import com.sakurakugu.archweaver.skin.SkinSavedData;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** 客户端缓存服务端同步的 PNG 皮肤，并注册动态纹理。 */
public final class ClientSkinCache {
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static final Map<UUID, Integer> REVISIONS = new HashMap<>();
    private static final Map<UUID, Source> SOURCES = new HashMap<>();
    private static long session;
    private ClientSkinCache() { }
    public static void accept(SkinSyncPayload payload) {
        clear(payload.target());
        if (payload.clear()) return;
        // 来源在收到服务端确认时立即更新，不依赖异步纹理下载是否完成。
        SOURCES.put(payload.target(), new Source(payload.targetType(), payload.kind(), payload.sourceName(), payload.fileName()));
        if (payload.kind() == SkinSavedData.Kind.PROFILE) {
            int revision = REVISIONS.getOrDefault(payload.target(), 0);
            long currentSession = session;
            ArrayListMultimap<String, Property> properties = ArrayListMultimap.create();
            payload.properties().forEach(value -> properties.put(value.name(), value.signature().isEmpty()
                ? new Property(value.name(), value.value()) : new Property(value.name(), value.value(), value.signature())));
            GameProfile profile = new GameProfile(payload.sourceUuid(), payload.sourceName(), new PropertyMap(properties));
            // 没有纹理属性的离线档案也必须使用来源玩家的默认外观。
            ENTRIES.put(payload.target(), Entry.profile(DefaultPlayerSkin.get(profile)));
            Minecraft.getInstance().getSkinManager().get(profile).thenAccept(result -> result.ifPresent(skin ->
                Minecraft.getInstance().execute(() -> {
                    if (session == currentSession && REVISIONS.getOrDefault(payload.target(), 0) == revision)
                        ENTRIES.put(payload.target(), Entry.profile(skin));
                })));
            return;
        }
        try {
            SkinPng.Decoded decoded = SkinPng.read(payload.png(), payload.model());
            Identifier id = Identifier.fromNamespaceAndPath("archweaver", "skin/" + payload.target());
            DynamicTexture texture = null;
            try {
                texture = new DynamicTexture(() -> "archweaver_skin", decoded.image());
                Minecraft.getInstance().getTextureManager().register(id, texture);
            } catch (RuntimeException exception) {
                if (texture != null) texture.close();
                else decoded.image().close();
                throw exception;
            }
            ENTRIES.put(payload.target(), Entry.png(id, decoded.model()));
        } catch (IOException | RuntimeException exception) {
            ArchWeaverMod.LOGGER.warn("无法加载目标 {} 的 PNG 皮肤", payload.target(), exception);
        }
    }
    public static PlayerSkin apply(UUID uuid, PlayerSkin original) {
        Entry entry = ENTRIES.get(uuid);
        if (entry == null) return original;
        if (entry.skin() != null) return entry.skin();
        return pngSkin(original, entry.texture(), entry.model());
    }
    static PlayerSkin pngSkin(PlayerSkin original, Identifier texture, PlayerModelType model) {
        // 动态纹理已注册完整路径，不能再由单参构造器补上 textures/ 和 .png。
        return original.with(PlayerSkin.Patch.create(java.util.Optional.of(new ClientAsset.ResourceTexture(texture, texture)), java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(model)));
    }
    public static void clear(UUID uuid) {
        REVISIONS.merge(uuid, 1, Integer::sum);
        SOURCES.remove(uuid);
        Entry old = ENTRIES.remove(uuid);
        if (old != null && old.texture() != null && Minecraft.getInstance() != null)
            Minecraft.getInstance().getTextureManager().release(old.texture());
    }
    public static Source source(UUID uuid, SkinSavedData.TargetType type) {
        Source source = SOURCES.get(uuid);
        return source != null && source.type() == type ? source : null;
    }

    /** 退出世界时释放动态纹理，同时让上个连接的异步回调失效。 */
    public static void clearAll() {
        session++;
        for (UUID uuid : java.util.Set.copyOf(SOURCES.keySet())) clear(uuid);
        for (UUID uuid : java.util.Set.copyOf(ENTRIES.keySet())) clear(uuid);
        REVISIONS.clear();
    }

    public record Source(SkinSavedData.TargetType type, SkinSavedData.Kind kind, String sourceName, String fileName) { }
    private record Entry(Identifier texture, PlayerModelType model, PlayerSkin skin) {
        static Entry png(Identifier texture, PlayerModelType model) { return new Entry(texture, model, null); }
        static Entry profile(PlayerSkin skin) { return new Entry(null, null, skin); }
    }
}
