package dev.xpple.simplewaypoints.impl;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.xpple.simplewaypoints.SimpleWaypoints;
import dev.xpple.simplewaypoints.api.Waypoint;
import dev.xpple.simplewaypoints.config.Configs;
import dev.xpple.simplewaypoints.render.EndMainPassEvent;
import dev.xpple.simplewaypoints.render.ExtractStateEvent;
import dev.xpple.simplewaypoints.render.NoDepthLayer;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

    private static final ResourceLocation HUD_LAYER_ID = ResourceLocation.fromNamespaceAndPath(SimpleWaypoints.MOD_ID, "waypoints");

    public static void registerEvents() {
        HudElementRegistry.addLast(HUD_LAYER_ID, WaypointRenderingHelper::renderWaypointLabels);
        ExtractStateEvent.EXTRACT_STATE.register(WaypointRenderingHelper::extractWaypointBoxes);
        EndMainPassEvent.END_MAIN_PASS.register(WaypointRenderingHelper::renderWaypointBoxes);
    }

    private static void renderWaypointLabels(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        String worldIdentifier = SimpleWaypointsImpl.INSTANCE.getWorldIdentifier(Minecraft.getInstance());
        Map<String, Waypoint> worldWaypoints = SimpleWaypointsImpl.waypoints.get(worldIdentifier);
        if (worldWaypoints == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer gameRenderer = minecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Entity cameraEntity = camera.getEntity();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        double verticalFovRad = Math.toRadians(gameRenderer.getFov(camera, partialTicks, true));
        Window window = minecraft.getWindow();
        double aspectRatio = (double) window.getGuiScaledWidth() / window.getGuiScaledHeight();
        double horizontalFovRad = 2 * Math.atan(Math.tan(verticalFovRad / 2) * aspectRatio);

        Vec3 viewVector3 = cameraEntity.getViewVector(1.0f);
        Vector2d viewVector = new Vector2d(viewVector3.x, viewVector3.z);
        Vector2d position = new Vector2d(cameraEntity.getEyePosition().x, cameraEntity.getEyePosition().z);

        List<WaypointLabelLocation> xPositions = new ArrayList<>();
        worldWaypoints.forEach((waypointName, waypoint) -> {
            if (!waypoint.dimension().location().equals(minecraft.level.dimension().location())) {
                return;
            }
            if (!waypoint.visible()) {
                return;
            }

            double distanceSquared = waypoint.location().distToCenterSqr(cameraEntity.position());
            long distance = Math.round(Math.sqrt(distanceSquared));
            if (Configs.waypointLabelRenderLimit >= 0 && distance > Configs.waypointLabelRenderLimit) {
                return;
            }
            Component label = ComponentUtils.wrapInSquareBrackets(Component.literal(waypointName + ' ' + distance).withStyle(ChatFormatting.YELLOW));
            Vec3 waypointCenter = waypoint.location().getCenter();

            Vector2d waypointLocation = new Vector2d(waypointCenter.x, waypointCenter.z);
            double angleRad = viewVector.angle(waypointLocation.sub(position, new Vector2d()));
            boolean right = angleRad > 0;
            angleRad = Math.abs(angleRad);

            int x;
            if (angleRad > horizontalFovRad / 2) {
                int width = minecraft.font.width(label);
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
                x = (int) (perc * guiGraphics.guiWidth());
            }
            xPositions.add(new WaypointLabelLocation(label, x));
        });

        xPositions.sort(Comparator.comparingInt(WaypointLabelLocation::location));

        List<List<WaypointLabelLocation>> positions = new ArrayList<>();
        positions.add(xPositions);

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointLabelLocation> waypointLabelLocations = positions.get(line);
            int i = 0;
            while (i < waypointLabelLocations.size() - 1) {
                WaypointLabelLocation left = waypointLabelLocations.get(i);
                WaypointLabelLocation right = waypointLabelLocations.get(i + 1);
                int leftX = left.location();
                int rightX = right.location();
                int leftWidth = minecraft.font.width(left.label());
                int rightWidth = minecraft.font.width(right.label());
                if (leftWidth / 2 + rightWidth / 2 > rightX - leftX) {
                    if (line + 1 == positions.size()) {
                        positions.add(new ArrayList<>());
                    }
                    List<WaypointLabelLocation> nextLevel = positions.get(line + 1);
                    WaypointLabelLocation removed = waypointLabelLocations.remove(i + 1);
                    nextLevel.add(removed);
                } else {
                    i++;
                }
            }
        }

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointLabelLocation> w = positions.get(line);
            for (WaypointLabelLocation waypoint : w) {
                guiGraphics.drawCenteredString(minecraft.font, waypoint.label(), waypoint.location(), 1 + line * minecraft.font.lineHeight, 0xFFFFFFFF);
            }
        }
    }

    private static void extractWaypointBoxes(LevelRenderState state, Camera camera, DeltaTracker deltaTracker) {
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
            if (!waypoint.dimension().location().equals(level.dimension().location())) {
                return;
            }
            if (!waypoint.visible()) {
                return;
            }

            BlockPos waypointLocation = waypoint.location();
            if (!chunkSource.hasChunk(waypointLocation.getX() >> 4, waypointLocation.getZ() >> 4)) {
                return;
            }

            Vec3 cameraPosition = camera.getPosition();
            float distance = (float) waypointLocation.distToCenterSqr(cameraPosition);
            distance = (float) Math.sqrt(distance);

            Vec3 relWaypointPosition = new Vec3(waypointLocation).subtract(cameraPosition);

            waypointStates.add(new WaypointState(waypointName, relWaypointPosition, distance / 6, waypoint.color(), Configs.waypointTextRenderLimit < 0 || distance < Configs.waypointTextRenderLimit, Configs.waypointLineBoxRenderLimit < 0 || distance < Configs.waypointLineBoxRenderLimit));
        });

        state.setData(WAYPOINT_LIST_KEY, new WaypointListState(camera.rotation(), waypointStates));
    }

    private static void renderWaypointBoxes(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, LevelRenderState state) {
        WaypointListState waypointList = state.getData(WAYPOINT_LIST_KEY);
        if (waypointList == null) {
            return;
        }

        for (WaypointState waypoint : waypointList.waypoints) {
            poseStack.pushPose();

            if (waypoint.renderLineBox) {
                AABB box = new AABB(waypoint.relPosition(), waypoint.relPosition().add(1));
                float red = ARGB.redFloat(waypoint.color());
                float green = ARGB.greenFloat(waypoint.color());
                float blue = ARGB.blueFloat(waypoint.color());
                ShapeRenderer.renderLineBox(poseStack.last(), bufferSource.getBuffer(NoDepthLayer.LINES_NO_DEPTH_LAYER), box, red, green, blue, 1);
            }

            if (waypoint.renderText) {
                poseStack.translate(waypoint.relPosition().add(0.5).add(new Vec3(0, 1, 0)));
                poseStack.mulPose(waypointList.cameraRotation());
                poseStack.scale(0.025f * waypoint.distance(), -0.025f * waypoint.distance(), 0.025f * waypoint.distance());

                Font font = Minecraft.getInstance().font;
                int width = font.width(waypoint.name()) / 2;
                int backgroundColour = (int) (Minecraft.getInstance().options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
                font.drawInBatch(waypoint.name(), -width, 0, 0xFF_FFFFFF, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH, backgroundColour, LightTexture.FULL_SKY);
            }

            poseStack.popPose();
        }
    }

    private record WaypointLabelLocation(Component label, int location) {
    }

    private record WaypointListState(Quaternionfc cameraRotation, List<WaypointState> waypoints) {
    }

    private record WaypointState(String name, Vec3 relPosition, float distance, int color, boolean renderText, boolean renderLineBox) {
    }
}
