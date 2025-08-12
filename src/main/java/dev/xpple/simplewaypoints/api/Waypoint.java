package dev.xpple.simplewaypoints.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.ApiStatus;

public record Waypoint(ResourceKey<Level> dimension, BlockPos location, boolean visible, int color) {
    @ApiStatus.Internal
    public Waypoint {
    }

    @ApiStatus.Internal
    public Waypoint(ResourceKey<Level> dimension, BlockPos location, int color) {
        this(dimension, location, true, color);
    }
}
