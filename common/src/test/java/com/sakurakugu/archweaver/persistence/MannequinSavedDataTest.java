package com.sakurakugu.archweaver.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.component.ResolvableProfile;
import org.junit.jupiter.api.Test;

class MannequinSavedDataTest {
    @Test
    void roundTripPreservesPoseSkinAndBothSnapshots() {
        UUID id = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        var angles = new MannequinSavedData.Angles(-90, 45, 180);
        var profile = ResolvableProfile.createResolved(new GameProfile(playerId, "Display"));
        var record = new MannequinSavedData.Record(id, "Display", "minecraft:the_nether", -0.5, 64, 100, 120,
            "CROUCHING", false, true, profile, 37, angles, MannequinSavedData.Angles.ZERO,
            new MannequinSavedData.Angles(10, 20, 30), angles);
        var entity = new CompoundTag();
        entity.putString("equipment_marker", "edited equipment");
        var player = new CompoundTag();
        player.putInt("selected_slot", 5);
        player.putString("inventory_marker", "original inventory");
        var data = new MannequinSavedData();
        data.put(record);
        data.putEntitySnapshot(id, entity);
        data.putPlayerSnapshot(id, player);

        var decoded = MannequinSavedData.CODEC.parse(NbtOps.INSTANCE,
            MannequinSavedData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow()).getOrThrow();
        var actual = decoded.find(id).orElseThrow();
        assertEquals(record.leftArm(), actual.leftArm());
        assertEquals(record.leftLeg(), actual.leftLeg());
        assertEquals(record.pose(), actual.pose());
        assertEquals(record.modelCustomisation(), actual.modelCustomisation());
        assertEquals(profile.partialProfile(), actual.profile().partialProfile());
        assertFalse(actual.immovable());
        assertTrue(actual.biologicalBehavior());
        assertEquals(entity, decoded.entitySnapshot(id).orElseThrow());
        assertEquals(player, decoded.playerSnapshot(id).orElseThrow());

        // 读取和写入都不能泄露可变 NBT，防止转换期间覆盖备份。
        var savedEntity = data.entitySnapshot(id).orElseThrow();
        entity.putString("equipment_marker", "changed");
        assertEquals(savedEntity, data.entitySnapshot(id).orElseThrow());
        var retrieved = decoded.playerSnapshot(id).orElseThrow();
        retrieved.putInt("selected_slot", 0);
        assertEquals(player, decoded.playerSnapshot(id).orElseThrow());
        assertTrue(decoded.remove(id));
        assertTrue(decoded.entitySnapshot(id).isEmpty());
        assertTrue(decoded.playerSnapshot(id).isEmpty());
    }

    @Test
    void emptyWorldDataCanBeSavedAndLoaded() {
        var decoded = MannequinSavedData.CODEC.parse(NbtOps.INSTANCE,
            MannequinSavedData.CODEC.encodeStart(NbtOps.INSTANCE, new MannequinSavedData()).getOrThrow()).getOrThrow();
        assertTrue(decoded.records().isEmpty());
    }

    @Test
    void invalidNetworkAnglesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MannequinSavedData.Angles(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MannequinSavedData.Angles(0, Float.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new MannequinSavedData.Angles(0, 0, 361));
    }
}
