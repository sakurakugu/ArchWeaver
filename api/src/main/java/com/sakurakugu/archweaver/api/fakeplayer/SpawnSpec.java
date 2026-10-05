package com.sakurakugu.archweaver.api.fakeplayer;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 生成一个假人需要的参数。{@code rotation} 的 x 是俯仰、y 是偏航。 */
public record SpawnSpec(
    String name,
    ServerLevel level,
    Vec3 position,
    Vec2 rotation,
    GameType gameType,
    boolean flying
) {
    /** 按常用默认值生成：生存模式、不飞行、朝向归零。 */
    public static SpawnSpec of(String name, ServerLevel level, Vec3 position) {
        return new SpawnSpec(name, level, position, Vec2.ZERO, GameType.SURVIVAL, false);
    }
}
