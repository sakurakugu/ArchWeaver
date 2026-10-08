package com.sakurakugu.archweaver.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class TargetInfoPayloadTest {
    @Test
    void remoteTargetInfoPreservesLargeCoordinatesDimensionAndActualVitals() {
        for (boolean biological : List.of(false, true)) {
            var original = new TargetInfoPayload(17, UUID.randomUUID(), "Display", "矿场 一号",
                "example:other/world", -30_000_000, -64, 30_000_000, 7.5F, 40, 13, -12, 300, biological);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                TargetInfoPayload.STREAM_CODEC.encode(buffer, original);
                assertEquals(original, TargetInfoPayload.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void initialMenuDataBindsAssignedContainerWithoutLosingTargetIdentity() {
        var initial = new TargetInfoPayload(0, UUID.randomUUID(), "Display", "", "minecraft:the_end",
            100_000, 80, -200_000, 20, 20, 0, 300, 300, false);
        var bound = initial.forContainer(42);
        assertEquals(42, bound.containerId());
        assertEquals(initial, bound.forContainer(0));
    }
}
