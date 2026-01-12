package com.github.alexthe666.citadel.client.game;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.awt.*;
import java.util.Arrays;

// Citadel: Tetris mini-game simplified for 1.21 (BakedModel and ModelData APIs removed)
// Full implementation would require adapting to 1.21's completely redesigned model system
public class Tetris {

    protected final RandomSource random = RandomSource.create();

    private boolean started = false;
    private int score;
    private int renderTime = 0;
    private int keyCooldown;
    private static int HEIGHT = 20;
    private TetrominoShape fallingShape;
    private BlockState fallingBlock;
    private float fallingX;
    private float prevFallingY;
    private float fallingY;
    private Rotation fallingRotation;
    private BlockState[][] settledBlocks = new BlockState[10][HEIGHT];
    private boolean gameOver = false;

    private TetrominoShape nextShape;
    private BlockState nextBlock;

    private boolean[] flashingLayer = new boolean[HEIGHT];
    private int flashFor = 0;

    public Tetris() {
        reset();
    }

    public void tick() {
        renderTime++;
        prevFallingY = fallingY;
        if (keyCooldown > 0) {
            keyCooldown--;
        }
        if (started && !gameOver) {
            if (fallingShape == null) {
                generateTetromino();
                generateNextTetromino();
            } else if (groundedTetromino()) {
                groundTetromino();
                fallingShape = null;
            } else {
                float f = 0.15F;
                if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_DOWN)) {
                    f = 1F;
                }
                fallingY += f;
                if (keyPressed(InputConstants.KEY_LEFT) && !isBlocksInOffset(-1, 0)) {
                    fallingX = restrictTetrominoX((int) (Math.floor(fallingX) - 1));
                }
                if (keyPressed(InputConstants.KEY_RIGHT) && !isBlocksInOffset(1, 0)) {
                    fallingX = restrictTetrominoX((int) (Math.ceil(fallingX) + 1));
                }
                if (keyPressed(InputConstants.KEY_UP) && fallingRotation != null && fallingShape != TetrominoShape.SQUARE) {
                    fallingRotation = fallingRotation.getRotated(Rotation.CLOCKWISE_90);
                    fallingX = restrictTetrominoX((int) (Math.floor(fallingX)));
                }
            }
        }
        if (flashFor > 0) {
            flashFor--;
            if (flashFor == 0) {
                for (int j = 0; j < HEIGHT; j++) {
                    if (flashingLayer[j]) {
                        for (int k = j; k < HEIGHT; k++) {
                            for (int i = 0; i < 10; i++) {
                                settledBlocks[i][k] = k < HEIGHT - 1 ? settledBlocks[i][k + 1] : null;
                            }
                        }
                    }
                }
                int cleared = 0;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
                for (int i = 0; i < flashingLayer.length; i++) {
                    if (flashingLayer[i]) {
                        cleared++;
                    }
                    flashingLayer[i] = false;
                }
                if (cleared == 1) {
                    score += 40;
                } else if (cleared == 2) {
                    score += 100;
                } else if (cleared == 3) {
                    score += 300;
                } else if (cleared >= 4) {
                    score += 1200 * (cleared - 3);
                }
            }
        }
        if (keyPressed(InputConstants.KEY_T)) {
            started = true;
            reset();
        }
    }

    private boolean groundedTetromino() {
        for (Vec3i vec : fallingShape.getRelativePositions()) {
            Vec3i vec2 = transform(vec, fallingRotation, Vec3i.ZERO);
            int x = Math.round(fallingX) + vec2.getX();
            int y = HEIGHT - (int) Math.ceil(fallingY) - vec2.getY();
            if (y < 0) {
                return true;
            }
            if (x >= 0 && x < 10 && y < HEIGHT) {
                if (y == 0 || settledBlocks[x][y - 1] != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void groundTetromino() {
        for (Vec3i vec : fallingShape.getRelativePositions()) {
            Vec3i vec2 = transform(vec, fallingRotation, Vec3i.ZERO);
            int x = Math.round(fallingX) + vec2.getX();
            int y = HEIGHT - (int) Math.ceil(fallingY) - vec2.getY();
            if (x >= 0 && x < 10 && y >= 0 && y < HEIGHT) {
                if (y >= HEIGHT - 1) {
                    gameOver = true;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_DEATH, 1.0F));
                }
                if (settledBlocks[x][y] == null) {
                    settledBlocks[x][y] = fallingBlock;
                }
            }
        }
        boolean flag = false;
        for (int j = 0; j < HEIGHT; j++) {
            for (int i = 0; i < 10; i++) {
                if (settledBlocks[i][j] == null) {
                    break;
                }
                if (i == 9) {
                    flashingLayer[j] = true;
                    flag = true;
                    break;
                }
            }
        }
        if (flag) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
            flashFor = 20;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(fallingBlock.getSoundType().getPlaceSound(), 1.0F));
    }

    private boolean isBlocksInOffset(int xOffset, int yOffset) {
        for (Vec3i vec : fallingShape.getRelativePositions()) {
            Vec3i vec2 = transform(vec, fallingRotation, Vec3i.ZERO);
            int x = Math.round(fallingX) + vec2.getX() + xOffset;
            int y = HEIGHT - (int) Math.ceil(fallingY) - vec2.getY() + yOffset;
            if (x >= 0 && x < 10 && y >= 0 && y < HEIGHT) {
                if (y == 0 || settledBlocks[x][y] != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isStarted() {
        return started;
    }

    private boolean keyPressed(int keyId) {
        if (keyCooldown == 0 && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), keyId)) {
            keyCooldown = 4;
            return true;
        }
        return false;
    }

    private void generateNextTetromino() {
        // Citadel: Simplified for 1.21 - BakedModel APIs removed
        // TODO: Implement with 1.21's model system if needed
        nextShape = TetrominoShape.getRandom(random);
        nextBlock = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
    }

    private void generateTetromino() {
        fallingShape = nextShape;
        fallingBlock = nextBlock;
        fallingRotation = Rotation.getRandom(random);
        fallingX = restrictTetrominoX(random.nextInt(10));
        prevFallingY = 0;
        fallingY = -2;
    }

    private int restrictTetrominoX(int xIn) {
        int minShapeX = 0;
        int maxShapeX = 0;
        for (Vec3i vec : fallingShape.getRelativePositions()) {
            Vec3i vec2 = transform(vec, fallingRotation, Vec3i.ZERO);
            if (vec2.getX() < minShapeX) {
                minShapeX = vec2.getX();
            }
            if (vec2.getX() > maxShapeX) {
                maxShapeX = vec2.getX();
            }
        }
        if (xIn + minShapeX < 0) {
            xIn = Math.max(xIn - minShapeX, minShapeX);
        }
        if (xIn + maxShapeX > 9) {
            xIn = Math.min(xIn - maxShapeX, 9 - maxShapeX);
        }
        return xIn;
    }

    private void renderTetromino(TetrominoShape shape, BlockState blockState, Rotation fallingRotation, float x, float y, float scale, float offsetX, float offsetY) {
        for (Vec3i vec : shape.getRelativePositions()) {
            Vec3i vec2 = transform(vec, fallingRotation, Vec3i.ZERO);
            renderBlockState(blockState, offsetX + (x + vec2.getX()) * scale, offsetY + (y + vec2.getY()) * scale, scale);
        }
    }

    private void renderBlockState(BlockState state, float offsetX, float offsetY, float size) {
        // Citadel: Simplified for 1.21 - BakedModel and ModelData APIs removed
        // TODO: Implement with 1.21's rendering system if this feature is needed
        // For now, rendering is disabled (Tetris mini-game not essential)
    }

    public void render(TitleScreen screen, GuiGraphics guiGraphics, float partialTick) {
        // Citadel: Simplified for 1.21 - rendering disabled (BakedModel APIs removed)
        // The Tetris mini-game is an optional Easter egg feature
        // Full implementation would require adapting to 1.21's rendering system
        if (!started) {
            return;
        }
        // TODO: Implement rendering with 1.21 APIs if needed
    }

    public void reset() {
        score = 0;
        for (BlockState[] settledBlock : settledBlocks) {
            Arrays.fill(settledBlock, null);
        }
        gameOver = false;
        Arrays.fill(flashingLayer, false);
        generateNextTetromino();
        generateTetromino();
        generateNextTetromino();
    }

    private static Vec3i transform(Vec3i vec3i, Rotation rotation, Vec3i relativeTo) {
        int i = vec3i.getX();
        int k = vec3i.getY();
        int j = vec3i.getZ();
        boolean flag = true;

        int l = relativeTo.getX();
        int i1 = relativeTo.getY();
        return switch (rotation) {
            case COUNTERCLOCKWISE_90 -> new Vec3i(l - i1 + k, l + i1 - i, j);
            case CLOCKWISE_90 -> new Vec3i(l + i1 - k, i1 - l + i, j);
            case CLOCKWISE_180 -> new Vec3i(l + l - i, i1 + i1 - k, j);
            default -> new Vec3i(i, k, j);
        };
    }

}
