package dev.xpple.simplewaypoints.impl;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.logging.LogUtils;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import dev.xpple.simplewaypoints.api.Waypoint;
import dev.xpple.simplewaypoints.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SimpleWaypointsImpl implements SimpleWaypointsAPI {
    public static final SimpleWaypointsImpl INSTANCE = new SimpleWaypointsImpl();

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> commandAliases = new HashSet<>();

    static final Map<String, Map<String, Waypoint>> waypoints = new HashMap<>();

    private static final DynamicCommandExceptionType INVALID_WAYPOINT_NAME_EXCEPTION = new DynamicCommandExceptionType(name -> Component.translatable("commands.sw:waypoint.invalidWaypointName", name));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(name -> Component.translatable("commands.sw:waypoint.alreadyExists", name));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(name -> Component.translatable("commands.sw:waypoint.notFound", name));

    static {
        try {
            SerializationHelper.loadFile();
        } catch (Exception e) {
            LOGGER.error("Could not load waypoints file, hence the waypoints will not work!", e);
        }
    }

    @Override
    public void registerCommandAlias(String alias) {
        commandAliases.add(alias);
    }

    @Override
    public Set<String> getCommandAliases() {
        return Collections.unmodifiableSet(commandAliases);
    }

    @Override
    public String getWorldIdentifier(Minecraft minecraft) {
        if (minecraft.hasSingleplayerServer()) {
            IntegratedServer singleplayerServer = Objects.requireNonNull(minecraft.getSingleplayerServer());
            // the level id remains the same even after the level is renamed
            return singleplayerServer.storageSource.getLevelId();
        }
        ClientPacketListener packetListener = Objects.requireNonNull(minecraft.getConnection());
        ServerData serverData = Objects.requireNonNull(packetListener.getServerData());
        if (serverData.isRealm()) {
            return Objects.requireNonNull(minecraft.quickPlayLog().worldData).id;
        }
        return packetListener.getConnection().getRemoteAddress().toString();
    }

    @Override
    public Map<String, Map<String, Waypoint>> getAllWaypoints() {
        return waypoints.entrySet()
            .stream()
            .map(entry -> Map.entry(entry.getKey(), Collections.unmodifiableMap(entry.getValue())))
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Map<String, Waypoint> getWorldWaypoints(String worldIdentifier) {
        Map<String, Waypoint> worldWaypoints = waypoints.getOrDefault(worldIdentifier, Collections.emptyMap());
        return Collections.unmodifiableMap(worldWaypoints);
    }

    @Override
    public int addWaypoint(String worldIdentifier, ResourceKey<Level> dimension, String name, BlockPos pos) throws CommandSyntaxException {
        return addWaypoint(worldIdentifier, dimension, name, pos, Configs.defaultColor.color());
    }

    @Override
    public int addWaypoint(String worldIdentifier, ResourceKey<Level> dimension, String name, BlockPos pos, int color) throws CommandSyntaxException {
        String parsedString = StringArgumentType.word().parse(new StringReader(name));
        if (!parsedString.equals(name)) {
            throw INVALID_WAYPOINT_NAME_EXCEPTION.create(name);
        }
        Map<String, Waypoint> worldWaypoints = waypoints.computeIfAbsent(worldIdentifier, key -> new HashMap<>());

        if (worldWaypoints.putIfAbsent(name, new Waypoint(dimension, pos, color)) != null) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        SerializationHelper.saveFile();
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public int removeWaypoint(String worldIdentifier, String name) throws CommandSyntaxException {
        Map<String, Waypoint> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.remove(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        SerializationHelper.saveFile();
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public int renameWaypoint(String worldIdentifier, String name, String newName) throws CommandSyntaxException {
        Map<String, Waypoint> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Waypoint waypoint = worldWaypoints.remove(name);
        if (waypoint == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        String parsedString = StringArgumentType.word().parse(new StringReader(name));
        if (!parsedString.equals(name)) {
            throw INVALID_WAYPOINT_NAME_EXCEPTION.create(name);
        }

        worldWaypoints.put(newName, waypoint);

        SerializationHelper.saveFile();
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public int editWaypoint(String worldIdentifier, String name, BlockPos pos) throws CommandSyntaxException {
        Map<String, Waypoint> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.computeIfPresent(name, (key, value) -> new Waypoint(value.dimension(), pos, value.visible(), value.color())) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        SerializationHelper.saveFile();
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public int editWaypoint(String worldIdentifier, String name, ResourceKey<Level> dimension, BlockPos pos) throws CommandSyntaxException {
        Map<String, Waypoint> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.computeIfPresent(name, (key, value) -> new Waypoint(dimension, pos, value.visible(), value.color())) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        SerializationHelper.saveFile();
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public int setWaypointVisibility(String worldIdentifier, String name, boolean visible) throws CommandSyntaxException {
        Map<String, Waypoint> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.computeIfPresent(name, (key, value) -> new Waypoint(value.dimension(), value.location(), visible, value.color())) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        SerializationHelper.saveFile();
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public int setWaypointColor(String worldIdentifier, String name, int color) throws CommandSyntaxException {
        Map<String, Waypoint> worldWaypoints = waypoints.get(worldIdentifier);

        if (worldWaypoints == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        if (worldWaypoints.computeIfPresent(name, (key, value) -> new Waypoint(value.dimension(), value.location(), value.visible(), color)) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        SerializationHelper.saveFile();
        return Command.SINGLE_SUCCESS;
    }
}
