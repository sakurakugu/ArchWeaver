package com.sakurakugu.archweaver.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;

/** 玩偶登记名称、原版描述和皮肤档案之间的转换规则。 */
public final class MannequinIdentity {
    private MannequinIdentity() { }

    public static Component defaultDescription() {
        return Component.translatable("entity.minecraft.mannequin.label");
    }

    public static String alias(Component description) {
        return description == null || description.equals(defaultDescription()) ? "" : description.getString();
    }

    public static Component description(String alias) {
        String normalized = FakePlayerAlias.normalize(alias);
        return normalized.isEmpty() ? defaultDescription() : Component.literal(normalized);
    }

    /** 切回玩家时只更改名称，UUID 和皮肤属性仍属于原身份。 */
    public static GameProfile playerProfile(GameProfile skinProfile, String name) {
        return new GameProfile(skinProfile.id(), name, skinProfile.properties());
    }
}
