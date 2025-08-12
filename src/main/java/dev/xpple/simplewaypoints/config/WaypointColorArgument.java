package dev.xpple.simplewaypoints.config;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.HexColorArgument;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class WaypointColorArgument implements ArgumentType<WaypointColor> {

    // delegate is stateless, so okay to share
    private static final HexColorArgument delegate = HexColorArgument.hexColor();

    private WaypointColorArgument() {
    }

    public static WaypointColorArgument waypointColor() {
        return new WaypointColorArgument();
    }

    @Override
    public WaypointColor parse(StringReader reader) throws CommandSyntaxException {
        return new WaypointColor(delegate.parse(reader));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return delegate.listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return delegate.getExamples();
    }
}
