package com.sakurakugu.archweaver.api.fakeplayer;

import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * 假人的只读快照。
 *
 * <p>取出那一刻的状态，不会跟随假人变化；假人实体本身不对外暴露。
 */
public record FakePlayerInfo(UUID id, String name, Identifier dimension, Vec3 position) {
}
