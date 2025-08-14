package dev.xpple.simplewaypoints.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import dev.xpple.simplewaypoints.api.Waypoint;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.util.BooleanFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.BoolArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CDimensionArgument.*;
import static dev.xpple.clientarguments.arguments.CHexColorArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static net.minecraft.commands.SharedSuggestionProvider.*;

public class WaypointCommand {
    private static final SimpleWaypointsAPI API = SimpleWaypointsAPI.getInstance();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralCommandNode<FabricClientCommandSource> waypointNode = dispatcher.register(literal("sw:waypoint")
            .then(literal("add")
                .then(argument("name", word())
                    .then(argument("pos", blockPos())
                        .executes(ctx -> add(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos")))
                        .then(argument("dimension", dimension())
                            .executes(ctx -> add(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos"), getDimension(ctx, "dimension")))
                            .then(argument("color", hexColor())
                                .executes(ctx -> add(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos"), getDimension(ctx, "dimension"), getHexColor(ctx, "color"))))))))
            .then(literal("remove")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> getWaypointSuggestions(ctx, builder, WaypointSuggestions.ALL))
                    .executes(ctx -> remove(ctx.getSource(), getString(ctx, "name")))))
            .then(literal("rename")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> getWaypointSuggestions(ctx, builder, WaypointSuggestions.ALL))
                    .then(argument("newname", word())
                        .executes(ctx -> rename(ctx.getSource(), getString(ctx, "name"), getString(ctx, "newname"))))))
            .then(literal("edit")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> getWaypointSuggestions(ctx, builder, WaypointSuggestions.ALL))
                    .then(argument("pos", blockPos())
                        .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos")))
                        .then(argument("dimension", dimension())
                            .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name"), getBlockPos(ctx, "pos"), getDimension(ctx, "dimension")))))))
            .then(literal("setcolor")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> getWaypointSuggestions(ctx, builder, WaypointSuggestions.ALL))
                    .then(argument("color", hexColor())
                        .executes(ctx -> setColor(ctx.getSource(), getString(ctx, "name"), getHexColor(ctx, "color"))))))
            .then(literal("hide")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> getWaypointSuggestions(ctx, builder, WaypointSuggestions.SHOWN))
                    .executes(ctx -> setVisibility(ctx.getSource(), getString(ctx, "name"), false))))
            .then(literal("show")
                .then(argument("name", word())
                    .suggests((ctx, builder) -> getWaypointSuggestions(ctx, builder, WaypointSuggestions.HIDDEN))
                    .executes(ctx -> setVisibility(ctx.getSource(), getString(ctx, "name"), true))))
            .then(literal("list")
                .executes(ctx -> list(ctx.getSource()))
                .then(argument("current", bool())
                    .executes(ctx -> list(ctx.getSource(), getBool(ctx, "current"))))));

        API.getCommandAliases().forEach(alias -> dispatcher.register(literal(alias).redirect(waypointNode)));
    }

    private enum WaypointSuggestions {
        ALL,
        SHOWN,
        HIDDEN,
        ;
    }

    private static CompletableFuture<Suggestions> getWaypointSuggestions(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder, WaypointSuggestions waypointSuggestions) {
        Map<String, Waypoint> worldWaypoints = API.getWorldWaypoints(API.getWorldIdentifier(ctx.getSource().getClient()));
        if (waypointSuggestions == WaypointSuggestions.ALL) {
            return suggest(worldWaypoints.keySet(), builder);
        }
        boolean suggestVisible = waypointSuggestions == WaypointSuggestions.HIDDEN;
        Stream<String> suggestions = worldWaypoints.entrySet().stream()
            .mapMulti((entry, consumer) -> {
                if (entry.getValue().visible() != suggestVisible) {
                    consumer.accept(entry.getKey());
                }
            });
        return suggest(suggestions, builder);
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos) throws CommandSyntaxException {
        return add(source, name, pos, source.getWorld().dimension());
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos, ResourceKey<Level> dimension) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.addWaypoint(worldIdentifier, dimension, name, pos);
        source.sendFeedback(Component.translatable("commands.sw:waypoint.add.success", name, formatCoordinates(pos), dimension.location()));
        return returnValue;
    }

    private static int add(FabricClientCommandSource source, String name, BlockPos pos, ResourceKey<Level> dimension, int color) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.addWaypoint(worldIdentifier, dimension, name, pos, color);
        source.sendFeedback(Component.translatable("commands.sw:waypoint.addWithColor.success", name, formatCoordinates(pos), dimension.location(), Integer.toHexString(color)));
        return returnValue;
    }

    private static int remove(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.removeWaypoint(worldIdentifier, name);
        source.sendFeedback(Component.translatable("commands.sw:waypoint.remove.success", name));
        return returnValue;
    }

    private static int rename(FabricClientCommandSource source, String name, String newName) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.rename(worldIdentifier, name, newName);
        source.sendFeedback(Component.translatable("commands.sw:waypoint.rename.success", name, newName));
        return returnValue;
    }

    private static int edit(FabricClientCommandSource source, String name, BlockPos pos) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.editWaypoint(worldIdentifier, name, pos);
        source.sendFeedback(Component.translatable("commands.sw:waypoint.edit.success", name, formatCoordinates(pos)));
        return returnValue;
    }

    private static int edit(FabricClientCommandSource source, String name, BlockPos pos, ResourceKey<Level> dimension) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.editWaypoint(worldIdentifier, name, dimension, pos);
        source.sendFeedback(Component.translatable("commands.sw:waypoint.editWithDimension.success", name, formatCoordinates(pos), dimension.location()));
        return returnValue;
    }

    private static int setVisibility(FabricClientCommandSource source, String name, boolean visible) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.setWaypointVisibility(worldIdentifier, name, visible);
        if (visible) {
            source.sendFeedback(Component.translatable("commands.sw:waypoint.show.success", name));
        } else {
            source.sendFeedback(Component.translatable("commands.sw:waypoint.hide.success", name));
        }
        return returnValue;
    }

    private static int setColor(FabricClientCommandSource source, String name, int color) throws CommandSyntaxException {
        String worldIdentifier = API.getWorldIdentifier(source.getClient());
        int returnValue = API.setWaypointColor(worldIdentifier, name, color);
        source.sendFeedback(Component.translatable("commands.sw:waypoint.setcolor.success", name, Integer.toHexString(color)));
        return returnValue;
    }

    private static int list(FabricClientCommandSource source) {
        return list(source, true);
    }

    private static int list(FabricClientCommandSource source, boolean current) {
        BooleanFunction<Component> getVisibilityComponent = visible -> visible ? Component.translatable("commands.sw:waypoint.shown") : Component.translatable("commands.sw:waypoint.hidden");
        if (current) {
            String worldIdentifier = API.getWorldIdentifier(source.getClient());
            Map<String, Waypoint> worldWaypoints = API.getWorldWaypoints(worldIdentifier);

            if (worldWaypoints.isEmpty()) {
                source.sendFeedback(Component.translatable("commands.sw:waypoint.list.empty"));
                return 0;
            }

            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Component.translatable("commands.sw:waypoint.list", name, formatCoordinates(waypoint.location()), waypoint.dimension().location(), getVisibilityComponent.apply(waypoint.visible()))));
            return worldWaypoints.size();
        }

        if (API.getAllWaypoints().isEmpty()) {
            source.sendFeedback(Component.translatable("commands.sw:waypoint.list.empty"));
            return 0;
        }

        int[] count = {0};
        API.getAllWaypoints().forEach((worldIdentifier, worldWaypoints) -> {
            if (worldWaypoints.isEmpty()) {
                return;
            }

            count[0] += worldWaypoints.size();

            source.sendFeedback(Component.literal(worldIdentifier).append(":"));
            worldWaypoints.forEach((name, waypoint) -> source.sendFeedback(Component.translatable("commands.sw:waypoint.list", name, formatCoordinates(waypoint.location()), waypoint.dimension().location(), getVisibilityComponent.apply(waypoint.visible()))));
        });
        return count[0];
    }

    private static Component formatCoordinates(BlockPos waypoint) {
        return ComponentUtils.wrapInSquareBrackets(Component.literal(waypoint.toShortString())).withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent.CopyToClipboard("%d %d %d".formatted(waypoint.getX(), waypoint.getY(), waypoint.getZ())))
            .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click")))
        );
    }
}
