package dev.xpple.simplewaypoints.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import dev.xpple.simplewaypoints.api.Waypoint;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;
import static net.minecraft.commands.SharedSuggestionProvider.*;

public final class ImportCommand {
    private ImportCommand() {
    }

    private static final SimpleWaypointsAPI API = SimpleWaypointsAPI.getInstance();
    private static final SimpleCommandExceptionType SAME_WORLD_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.sw:import.sameWorld"));
    private static final DynamicCommandExceptionType NO_WAYPOINTS_TO_EXPORT_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.sw:import.noWaypointsToImport", arg));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("sw:import")
            .then(argument("other", greedyString())
                .suggests((_, builder) -> suggest(API.getAllWaypoints().keySet(), builder))
                .executes(ctx -> importWaypoints(ctx.getSource(), getString(ctx, "other")))));
    }

    private static int importWaypoints(FabricClientCommandSource source, String other) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        if (other.equals(worldIdentifier)) {
            throw SAME_WORLD_EXCEPTION.create();
        }

        Map<String, Waypoint> otherWaypoints = API.getWorldWaypoints(other);

        if (otherWaypoints.isEmpty()) {
            throw NO_WAYPOINTS_TO_EXPORT_EXCEPTION.create(other);
        }

        int successCount = 0;
        for (Map.Entry<String, Waypoint> entry : otherWaypoints.entrySet()) {
            String key = entry.getKey();
            Waypoint value = entry.getValue();
            try {
                API.addWaypoint(worldIdentifier, value.dimension(), key, value.location(), value.color());

                source.sendFeedback(Component.translatable("commands.sw:import.success", key));
                successCount++;
            } catch (CommandSyntaxException e) {
                Component message = (Component) e.getRawMessage();
                source.sendError(Component.translatable("commands.sw:import.failed", key, message));
            }
        }

        source.sendFeedback(Component.translatable("commands.sw:import.summary", successCount, other));
        return successCount;
    }
}
