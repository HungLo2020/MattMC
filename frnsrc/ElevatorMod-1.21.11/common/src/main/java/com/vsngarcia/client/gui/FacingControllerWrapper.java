package com.vsngarcia.client.gui;

import com.vsngarcia.network.ClientPacketSender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

class FacingControllerWrapper {

    private final HashSet<FacingButton> bakedButtons = new HashSet<>();
    private final ArrayList<Point> slots = new ArrayList<>();

    FacingControllerWrapper(
            int xIn,
            int yIn,
            BlockPos blockPos,
            Direction playerFacing,
            ClientPacketSender packetSender
    ) {
        initSlots(xIn, yIn);
        initButtons(playerFacing, blockPos, packetSender);
    }

    private void initSlots(int xIn, int yIn) {
        slots.add(new Point(xIn + 20, yIn)); // UP
        slots.add(new Point(xIn + 40, yIn + 20)); // RIGHT
        slots.add(new Point(xIn + 20, yIn + 40)); // BOTTOM
        slots.add(new Point(xIn, yIn + 20)); //LEFT
    }

    private void initButtons(Direction playerFacing, BlockPos pos, ClientPacketSender packetSender) {
        Collections.rotate(slots, playerFacing.get2DDataValue()); // Modifies list
        bakedButtons.add(new FacingButton(slots.get(0), Direction.SOUTH, pos, packetSender));
        bakedButtons.add(new FacingButton(slots.get(1), Direction.WEST, pos, packetSender));
        bakedButtons.add(new FacingButton(slots.get(2), Direction.NORTH, pos, packetSender));
        bakedButtons.add(new FacingButton(slots.get(3), Direction.EAST, pos, packetSender));
    }

    HashSet<FacingButton> getButtons() {
        return bakedButtons;
    }
}

