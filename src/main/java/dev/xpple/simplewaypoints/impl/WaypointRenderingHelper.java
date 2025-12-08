package dev.xpple.simplewaypoints.impl;

import com.mojang.blaze3d.platform.Window;
import dev.xpple.simplewaypoints.SimpleWaypoints;
import dev.xpple.simplewaypoints.api.Waypoint;
import dev.xpple.simplewaypoints.config.Configs;
import dev.xpple.simplewaypoints.render.NoDepthLayer;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionfc;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class WaypointRenderingHelper {
    private static final RenderStateDataKey<WaypointListState> WAYPOINT_LIST_KEY = RenderStateDataKey.create(() -> "SimpleWaypoints waypoint list");

    private WaypointRenderingHelper() {
    }

    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath(SimpleWaypoints.MOD_ID, "waypoints");

    public static void registerEvents() {
        HudElementRegistry.addLast(HUD_LAYER_ID, WaypointRenderingHelper::renderWaypointMarkers);
        WorldRenderEvents.END_EXTRACTION.register(WaypointRenderingHelper::extractWaypointBoxes);
        WorldRenderEvents.END_MAIN.register(WaypointRenderingHelper::renderWaypointBoxes);
    }

    private static void renderWaypointMarkers(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        String worldIdentifier = SimpleWaypointsImpl.INSTANCE.getWorldIdentifier(Minecraft.getInstance());
        Map<String, Waypoint> worldWaypoints = SimpleWaypointsImpl.waypoints.get(worldIdentifier);
        if (worldWaypoints == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer gameRenderer = minecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Entity cameraEntity = camera.entity();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        double verticalFovRad = Math.toRadians(gameRenderer.getFov(camera, partialTicks, true));
        Window window = minecraft.getWindow();
        double aspectRatio = (double) window.getGuiScaledWidth() / window.getGuiScaledHeight();
        double horizontalFovRad = 2 * Math.atan(Math.tan(verticalFovRad / 2) * aspectRatio);

        Vec3 viewVector3 = cameraEntity.getViewVector(1.0f);
        Vector2d viewVector = new Vector2d(viewVector3.x, viewVector3.z);
        Vector2d position = new Vector2d(cameraEntity.getEyePosition().x, cameraEntity.getEyePosition().z);

        List<WaypointMarkerLocation> xPositions = new ArrayList<>();
        worldWaypoints.forEach((waypointName, waypoint) -> {
            if (!waypoint.dimension().identifier().equals(minecraft.level.dimension().identifier())) {
                return;
            }
            if (!waypoint.visible()) {
                return;
            }

            double distanceSquared = waypoint.location().distToCenterSqr(cameraEntity.position());
            long distance = Math.round(Math.sqrt(distanceSquared));
            if (Configs.waypointMarkerRenderLimit >= 0 && distance > Configs.waypointMarkerRenderLimit) {
                return;
            }
            Component marker = ComponentUtils.wrapInSquareBrackets(Component.literal(waypointName + ' ' + distance).withStyle(ChatFormatting.YELLOW));
            Vec3 waypointCenter = waypoint.location().getCenter();

            Vector2d waypointLocation = new Vector2d(waypointCenter.x, waypointCenter.z);
            double angleRad = viewVector.angle(waypointLocation.sub(position, new Vector2d()));
            boolean right = angleRad > 0;
            angleRad = Math.abs(angleRad);

            int x;
            if (angleRad > horizontalFovRad / 2) {
                int width = minecraft.font.width(marker);
                x = right ? guiGraphics.guiWidth() - width / 2 : width / 2;
            } else {
                // V is the view vector
                // A is the leftmost visible direction
                // B is the rightmost visible direction
                // M is the intersection of the position -> waypoint line with AB
                double mv = Math.tan(angleRad) * GameRenderer.PROJECTION_Z_NEAR;
                double av = Math.tan(horizontalFovRad / 2) * GameRenderer.PROJECTION_Z_NEAR;
                double ab = 2 * av;
                double am = right ? mv + av : ab - (mv + av);
                double perc = am / ab;
                int guiWidth = guiGraphics.guiWidth();
                int halfWidth = minecraft.font.width(marker) / 2;
                x = Math.clamp((int) (perc * guiWidth), halfWidth, guiWidth - halfWidth);
            }
            xPositions.add(new WaypointMarkerLocation(marker, x));
        });

        xPositions.sort(Comparator.comparingInt(WaypointMarkerLocation::location));

        List<List<WaypointMarkerLocation>> positions = new ArrayList<>();
        positions.add(xPositions);

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointMarkerLocation> waypointMarkerLocations = positions.get(line);
            int i = 0;
            while (i < waypointMarkerLocations.size() - 1) {
                WaypointMarkerLocation left = waypointMarkerLocations.get(i);
                WaypointMarkerLocation right = waypointMarkerLocations.get(i + 1);
                int leftX = left.location();
                int rightX = right.location();
                int leftWidth = minecraft.font.width(left.marker());
                int rightWidth = minecraft.font.width(right.marker());
                if (leftWidth / 2 + rightWidth / 2 > rightX - leftX) {
                    if (line + 1 == positions.size()) {
                        positions.add(new ArrayList<>());
                    }
                    List<WaypointMarkerLocation> nextLevel = positions.get(line + 1);
                    WaypointMarkerLocation removed = waypointMarkerLocations.remove(i + 1);
                    nextLevel.add(removed);
                } else {
                    i++;
                }
            }
        }

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointMarkerLocation> w = positions.get(line);
            for (WaypointMarkerLocation waypoint : w) {
                guiGraphics.drawCenteredString(minecraft.font, waypoint.marker(), waypoint.location(), 1 + line * minecraft.font.lineHeight, 0xFFFFFFFF);
            }
        }
    }

    private static void extractWaypointBoxes(WorldExtractionContext context) {
        String worldIdentifier = SimpleWaypointsImpl.INSTANCE.getWorldIdentifier(Minecraft.getInstance());
        Map<String, Waypoint> worldWaypoints = SimpleWaypointsImpl.waypoints.get(worldIdentifier);
        if (worldWaypoints == null) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        ClientChunkCache chunkSource = level.getChunkSource();

        List<WaypointState> waypointStates = new ArrayList<>();
        worldWaypoints.forEach((waypointName, waypoint) -> {
            if (!waypoint.dimension().identifier().equals(level.dimension().identifier())) {
                return;
            }
            if (!waypoint.visible()) {
                return;
            }

            BlockPos waypointLocation = waypoint.location();
            if (!chunkSource.hasChunk(waypointLocation.getX() >> 4, waypointLocation.getZ() >> 4)) {
                return;
            }

            Vec3 cameraPosition = context.camera().position();
            float distance = (float) Math.sqrt(waypointLocation.distToCenterSqr(cameraPosition));

            Vec3 relWaypointPosition = new Vec3(waypointLocation).subtract(cameraPosition);

            boolean renderLabel = Configs.waypointLabelRenderLimit < 0 || distance <= Configs.waypointLabelRenderLimit;
            boolean renderLineBox = Configs.waypointLineBoxRenderLimit < 0 || distance <= Configs.waypointLineBoxRenderLimit;
            waypointStates.add(new WaypointState(waypointName, relWaypointPosition, distance, waypoint.color(), renderLabel, renderLineBox));
        });

        context.worldState().setData(WAYPOINT_LIST_KEY, new WaypointListState(context.camera().rotation(), waypointStates));
    }

    private static void renderWaypointBoxes(WorldRenderContext context) {
        WaypointListState waypointList = context.worldState().getData(WAYPOINT_LIST_KEY);
        if (waypointList == null) {
            return;
        }

        for (WaypointState waypoint : waypointList.waypoints) {
            context.matrices().pushPose();

            if (waypoint.renderLineBox) {
                ShapeRenderer.renderShape( context.matrices(), context.consumers().getBuffer(NoDepthLayer.LINES_NO_DEPTH_LAYER), Shapes.block(), waypoint.relPosition().x, waypoint.relPosition().y, waypoint.relPosition().z, ARGB.opaque(waypoint.color()), 2);
            }

            if (waypoint.renderLabel) {
                context.matrices().translate(waypoint.relPosition().add(0.5).add(new Vec3(0, 1, 0)));
                context.matrices().mulPose(waypointList.cameraRotation());
                context.matrices().scale(1 / 6f, 1 / 6f, 1 / 6f);
                context.matrices().scale(0.025f * waypoint.distance(), -0.025f * waypoint.distance(), 0.025f * waypoint.distance());

                Font font = Minecraft.getInstance().font;
                int width = font.width(waypoint.name()) / 2;
                int backgroundColour = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
                font.drawInBatch(waypoint.name(), -width, 0, 0xFF_FFFFFF, false,  context.matrices().last().pose(), context.consumers(), Font.DisplayMode.SEE_THROUGH, backgroundColour, LightTexture.FULL_SKY);
            }

            context.matrices().popPose();
        }
    }

    private record WaypointMarkerLocation(Component marker, int location) {
    }

    private record WaypointListState(Quaternionfc cameraRotation, List<WaypointState> waypoints) {
    }

    private record WaypointState(String name, Vec3 relPosition, float distance, int color, boolean renderLabel, boolean renderLineBox) {
    }
}
