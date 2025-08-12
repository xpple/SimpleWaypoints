package dev.xpple.simplewaypoints;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.simplewaypoints.config.Configs;
import dev.xpple.simplewaypoints.impl.SerializationHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class WaypointLoadingTest {
    @BeforeAll
    public static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag parseSnbt(String snbt) {
        try {
            return TagParser.parseCompoundAsArgument(new StringReader(snbt));
        } catch (CommandSyntaxException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testWaypointLoading() {
        CompoundTag waypointTag = parseSnbt("""
            {
                DataVersion: 4189,
                Waypoints: {
                    foo: {
                        testWaypoint: {
                           pos: [I; 1, 2, 3],
                           Dimension: "minecraft:overworld",
                           visible: true,
                           color: 0xFF5555
                        }
                    }
                }
            }
            """);

        var waypoints = SerializationHelper.deserializeWaypoints(waypointTag);
        assertEquals(1, waypoints.size());
        assertTrue(waypoints.containsKey("foo"));
        var worldWaypoints = waypoints.get("foo");
        assertEquals(1, worldWaypoints.size());
        assertTrue(worldWaypoints.containsKey("testWaypoint"));
        var waypoint = worldWaypoints.get("testWaypoint");
        assertEquals(new BlockPos(1, 2, 3), waypoint.location());
        assertEquals(Level.OVERWORLD, waypoint.dimension());
        assertTrue(waypoint.visible());
        assertEquals(ChatFormatting.RED.getColor(), waypoint.color());
    }

    @Test
    public void testWaypointLoadingWithoutVisibleAndColorKey() {
        CompoundTag waypointTag = parseSnbt("""
            {
                DataVersion: 4189,
                Waypoints: {
                    foo: {
                        testWaypoint: {
                           pos: [I; 1, 2, 3],
                           Dimension: "minecraft:overworld"
                        }
                    }
                }
            }
            """);

        var waypoints = SerializationHelper.deserializeWaypoints(waypointTag);
        assertEquals(1, waypoints.size());
        assertTrue(waypoints.containsKey("foo"));
        var worldWaypoints = waypoints.get("foo");
        assertEquals(1, worldWaypoints.size());
        assertTrue(worldWaypoints.containsKey("testWaypoint"));
        var waypoint = worldWaypoints.get("testWaypoint");
        assertEquals(new BlockPos(1, 2, 3), waypoint.location());
        assertEquals(Level.OVERWORLD, waypoint.dimension());
        assertTrue(waypoint.visible());
        assertEquals(Configs.defaultColor.color(), waypoint.color());
    }
}
