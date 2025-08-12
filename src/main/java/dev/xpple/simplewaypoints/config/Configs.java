package dev.xpple.simplewaypoints.config;

import com.google.common.base.Suppliers;
import dev.xpple.betterconfig.api.BetterConfigAPI;
import dev.xpple.betterconfig.api.Config;
import dev.xpple.betterconfig.api.ModConfig;
import dev.xpple.simplewaypoints.SimpleWaypoints;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

@SuppressWarnings("unused")
public class Configs {
    public static final Supplier<ModConfig<Component>> CONFIG_REF = Suppliers.memoize(() -> BetterConfigAPI.getInstance().getModConfig(SimpleWaypoints.MOD_ID));

    public static void save() {
        Configs.CONFIG_REF.get().save();
    }

    @Config(chatRepresentation = "formatDefaultColor")
    public static WaypointColor defaultColor = new WaypointColor(0xFFFFFF);

    private static Component formatDefaultColor() {
        return Component.literal(Integer.toHexString(defaultColor.color()));
    }

    @Config(comment = "waypointLabelRenderLimitComment")
    public static int waypointLabelRenderLimit = -1;

    private static Component waypointLabelRenderLimitComment() {
        return Component.translatable("commands.sw:config.waypointLabelRenderLimit.comment");
    }
}
