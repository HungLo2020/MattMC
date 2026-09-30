package net.voxelmap.util;

import net.voxelmap.MapSettingsManager;
import net.voxelmap.VoxelConstants;
import net.voxelmap.textures.Sprite;
import net.voxelmap.textures.TextureAtlas;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class WaypointContainer {
    private final List<ExtendedWaypoint> wayPts = new ArrayList<>();
    private Waypoint highlightedWaypoint;
    public final MapSettingsManager options;
    public final Minecraft minecraft = Minecraft.getInstance();

    public static class ExtendedWaypoint implements Comparable<ExtendedWaypoint> {
        public Waypoint waypoint;
        public double offset;
        public boolean target;

        public ExtendedWaypoint(Waypoint waypoint) {
            this.waypoint = waypoint;
        }

        public int compareTo(ExtendedWaypoint o) {
            boolean skip1 = offset == -1.0 || (!waypoint.enabled && !target) || !waypoint.inWorld || !waypoint.inDimension;
            boolean skip2 = o.offset == -1.0 || (!o.waypoint.enabled && !o.target) || !o.waypoint.inWorld || !o.waypoint.inDimension;

            if (skip1 && !skip2) return 1;
            if (!skip1 && skip2) return -1;

            return Double.compare(offset, o.offset);
        }
    }


    public WaypointContainer(MapSettingsManager options) {
        this.options = options;
    }

    public void addWaypoint(Waypoint newWaypoint) {
        this.wayPts.add(new ExtendedWaypoint(newWaypoint));
    }

    public void removeWaypoint(Waypoint waypoint) {
        this.wayPts.removeIf(point -> point.waypoint == waypoint);
    }

    public void setHighlightedWaypoint(Waypoint highlightedWaypoint) {
        this.highlightedWaypoint = highlightedWaypoint;
    }

    private void renderBeam(Waypoint par1EntityWaypoint, double baseX, double baseY, double baseZ, PoseStack poseStack, BufferSource bufferSource) {
        int height = VoxelConstants.getClientWorld().getHeight();
        float brightness = 0.1F;
        double topWidthFactor = 1.05;
        double bottomWidthFactor = 1.05;
        float r = par1EntityWaypoint.red;
        float b = par1EntityWaypoint.blue;
        float g = par1EntityWaypoint.green;

        VertexConsumer vertexConsumerBeam = bufferSource.getBuffer(VoxelMapRenderTypes.WAYPOINT_BEAM);

        for (int width = 0; width < 4; ++width) {
            double d6 = 0.1 + width * 0.2;
            d6 *= topWidthFactor;
            double d7 = 0.1 + width * 0.2;
            d7 *= bottomWidthFactor;

            for (int side = 0; side < 5; ++side) {
                float vertX2 = (float) (baseX + 0.5 - d6);
                float vertZ2 = (float) (baseZ + 0.5 - d6);
                if (side == 1 || side == 2) {
                    vertX2 = (float) (vertX2 + d6 * 2.0);
                }

                if (side == 2 || side == 3) {
                    vertZ2 = (float) (vertZ2 + d6 * 2.0);
                }

                float vertX1 = (float) (baseX + 0.5 - d7);
                float vertZ1 = (float) (baseZ + 0.5 - d7);
                if (side == 1 || side == 2) {
                    vertX1 = (float) (vertX1 + d7 * 2.0);
                }

                if (side == 2 || side == 3) {
                    vertZ1 = (float) (vertZ1 + d7 * 2.0);
                }

                vertexConsumerBeam.addVertex(poseStack.last(), vertX1, (float) baseY + 0.0F, vertZ1).setColor(r * brightness, g * brightness, b * brightness, 0.8F);
                vertexConsumerBeam.addVertex(poseStack.last(), vertX2, (float) baseY + height, vertZ2).setColor(r * brightness, g * brightness, b * brightness, 0.8F);
            }
        }
    }

}
