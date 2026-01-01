package dev.xpple.simplewaypoints.impl;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import dev.xpple.simplewaypoints.SimpleWaypoints;
import dev.xpple.simplewaypoints.api.Waypoint;
import dev.xpple.simplewaypoints.config.Configs;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.VisibleForTesting;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SerializationHelper {
    private SerializationHelper() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.sw:waypoint.saveFailed"));

    static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            rootTag.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
            CompoundTag compoundTag = new CompoundTag();
            SimpleWaypointsImpl.waypoints.forEach((worldIdentifier, worldWaypoints) -> {
                if (!worldWaypoints.isEmpty()) {
                    compoundTag.put(worldIdentifier, worldWaypoints.entrySet().stream()
                            .collect(CompoundTag::new, (result, entry) -> {
                                        CompoundTag waypoint = new CompoundTag();
                                        waypoint.store("pos", BlockPos.CODEC, entry.getValue().location());
                                        String dimension = entry.getValue().dimension().identifier().toString();
                                        waypoint.putString("Dimension", dimension);
                                        waypoint.putBoolean("visible", entry.getValue().visible());
                                        waypoint.putInt("color", entry.getValue().color());
                                        result.put(entry.getKey(), waypoint);
                                    }, CompoundTag::merge));
                }
            });
            rootTag.put("Waypoints", compoundTag);
            Path newFile = Files.createTempFile(SimpleWaypoints.MOD_CONFIG_PATH, "waypoints", ".dat");
            NbtIo.write(rootTag, newFile);
            Path backupFile = SimpleWaypoints.MOD_CONFIG_PATH.resolve("waypoints.dat_old");
            Path currentFile = SimpleWaypoints.MOD_CONFIG_PATH.resolve("waypoints.dat");
            Util.safeReplaceFile(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    static void loadFile() throws Exception {
        SimpleWaypointsImpl.waypoints.clear();
        CompoundTag rootTag = NbtIo.read(SimpleWaypoints.MOD_CONFIG_PATH.resolve("waypoints.dat"));
        if (rootTag == null) {
            return;
        }
        SimpleWaypointsImpl.waypoints.putAll(deserializeWaypoints(rootTag));
    }

    @VisibleForTesting
    public static Map<String, Map<String, Waypoint>> deserializeWaypoints(CompoundTag rootTag) {
        Map<String, Map<String, Waypoint>> waypoints = new HashMap<>();

        CompoundTag compoundTag = rootTag.getCompoundOrEmpty("Waypoints");
        compoundTag.keySet().forEach(worldIdentifier -> {
            CompoundTag worldWaypoints = compoundTag.getCompoundOrEmpty(worldIdentifier);
            waypoints.put(worldIdentifier, worldWaypoints.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), name -> {
                    CompoundTag waypoint = worldWaypoints.getCompoundOrEmpty(name);
                    BlockPos pos = waypoint.read("pos", BlockPos.CODEC).orElseThrow();
                    ResourceKey<Level> dimension = Level.RESOURCE_KEY_CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, waypoint.get("Dimension"))).resultOrPartial(LOGGER::error).orElseThrow();
                    boolean visible = waypoint.getBooleanOr("visible", true);
                    int color = waypoint.getIntOr("color", Configs.defaultColor.color());
                    return new Waypoint(dimension, pos, visible, color);
                })));
        });

        return waypoints;
    }
}
