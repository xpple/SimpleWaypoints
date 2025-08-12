package dev.xpple.simplewaypoints.api;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.simplewaypoints.impl.SimpleWaypointsImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface SimpleWaypointsAPI {
    static SimpleWaypointsAPI getInstance() {
        return SimpleWaypointsImpl.INSTANCE;
    }

    @Nullable String getWorldIdentifier(Minecraft minecraft);

    @NotNull Map<String, Map<String, Waypoint>> getAllWaypoints();

    @NotNull Map<String, Waypoint> getWorldWaypoints(String worldIdentifier);

    int addWaypoint(String worldIdentifier, ResourceKey<Level> dimension, String name, BlockPos pos) throws CommandSyntaxException;

    int addWaypoint(String worldIdentifier, ResourceKey<Level> dimension, String name, BlockPos pos, int color) throws CommandSyntaxException;

    int removeWaypoint(String worldIdentifier, String name) throws CommandSyntaxException;

    int rename(String worldIdentifier, String name, String newName) throws CommandSyntaxException;

    int editWaypoint(String worldIdentifier, String name, BlockPos pos) throws CommandSyntaxException;

    int editWaypoint(String worldIdentifier, String name, ResourceKey<Level> dimension, BlockPos pos) throws CommandSyntaxException;

    int setWaypointVisibility(String worldIdentifier, String name, boolean visible) throws CommandSyntaxException;

    int setWaypointColor(String worldIdentifier, String name, int color) throws CommandSyntaxException;
}
