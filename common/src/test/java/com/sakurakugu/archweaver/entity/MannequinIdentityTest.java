package com.sakurakugu.archweaver.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.HashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class MannequinIdentityTest {
    @Test
    void nativeDefaultAndBlankAliasRestoreNpcButLiteralNpcRemainsCustom() {
        assertEquals("", MannequinIdentity.alias(Component.translatable("entity.minecraft.mannequin.label")));
        assertEquals("", MannequinIdentity.alias(null));
        assertEquals(MannequinIdentity.defaultDescription(), MannequinIdentity.description("  "));
        assertEquals("NPC", MannequinIdentity.alias(Component.literal("NPC")));
        assertEquals("矿场 一号", MannequinIdentity.alias(MannequinIdentity.description(" 矿场 一号 ")));
        assertThrows(IllegalArgumentException.class, () -> MannequinIdentity.description("矿".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> MannequinIdentity.description("矿§a场"));
    }

    @Test
    void renamedPlayerKeepsOriginalUuidAndSignedSkin() {
        var properties = HashMultimap.<String, Property>create();
        properties.put("textures", new Property("textures", "encoded skin", "signed value"));
        var skin = new GameProfile(UUID.randomUUID(), "OldName", new PropertyMap(properties));

        var renamed = MannequinIdentity.playerProfile(skin, "NewName");

        assertEquals("NewName", renamed.name());
        assertEquals(skin.id(), renamed.id());
        assertEquals(skin.properties(), renamed.properties());
        assertEquals("OldName", skin.name());
    }
}
