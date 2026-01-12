package com.github.alexthe666.citadel.client.model;

import com.github.alexthe666.citadel.client.model.basic.BasicModelPart;
import com.github.alexthe666.citadel.client.model.container.TabulaCubeContainer;
import com.github.alexthe666.citadel.client.model.container.TabulaCubeGroupContainer;
import com.github.alexthe666.citadel.client.model.container.TabulaModelContainer;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gegy1000
 * @since 1.0.0
 * 
 * Citadel: 1.21 - Changed to use EntityRenderState instead of Entity
 * Uses raw type since we don't know what specific EntityRenderState subclass will be used
 */
public class TabulaModel extends AdvancedEntityModel<EntityRenderState> {
    protected Map<String, AdvancedModelBox> cubes = new HashMap<>();
    protected List<AdvancedModelBox> rootBoxes = new ArrayList<>();
    protected ITabulaModelAnimator tabulaAnimator;
    public ModelAnimator llibAnimator;
    protected Map<String, AdvancedModelBox> identifierMap = new HashMap<>();
    protected double[] scale;

    public TabulaModel(TabulaModelContainer container, ITabulaModelAnimator tabulaAnimator) {
        super(ModelPart.EMPTY); // Citadel: 1.21 - Use dummy ModelPart since Tabula builds custom structure
        this.texWidth = container.getTextureWidth();
        this.texHeight = container.getTextureHeight();
        this.tabulaAnimator = tabulaAnimator;
        for (TabulaCubeContainer cube : container.getCubes()) {
            this.parseCube(cube, null);
        }
        container.getCubeGroups().forEach(this::parseCubeGroup);
        this.updateDefaultPose();
        this.scale = container.getScale();
        this.llibAnimator = ModelAnimator.create();
    }

    public TabulaModel(TabulaModelContainer container) {
        this(container, null);
    }

    private void parseCubeGroup(TabulaCubeGroupContainer container) {
        for (TabulaCubeContainer cube : container.getCubes()) {
            this.parseCube(cube, null);
        }
        container.getCubeGroups().forEach(this::parseCubeGroup);
    }

    private void parseCube(TabulaCubeContainer cube, AdvancedModelBox parent) {
        AdvancedModelBox box = this.createBox(cube);
        this.cubes.put(cube.getName(), box);
        this.identifierMap.put(cube.getIdentifier(), box);
        if (parent != null) {
            parent.addChild(box);
        } else {
            this.rootBoxes.add(box);
        }
        for (TabulaCubeContainer child : cube.getChildren()) {
            this.parseCube(child, box);
        }
    }

    private AdvancedModelBox createBox(TabulaCubeContainer cube) {
        int[] textureOffset = cube.getTextureOffset();
        double[] position = cube.getPosition();
        double[] rotation = cube.getRotation();
        double[] offset = cube.getOffset();
        int[] dimensions = cube.getDimensions();
        float scaleIn = 0;
        AdvancedModelBox box = new AdvancedModelBox(this, cube.getName());
        box.setTextureOffset(textureOffset[0], textureOffset[1]);
        box.mirror = cube.isTextureMirrorEnabled();
        box.setPos((float) position[0], (float) position[1], (float) position[2]);
        box.addBox((float) offset[0], (float) offset[1], (float) offset[2], dimensions[0], dimensions[1], dimensions[2], scaleIn);
        box.rotateAngleX = (float) Math.toRadians(rotation[0]);
        box.rotateAngleY = (float) Math.toRadians(rotation[1]);
        box.rotateAngleZ = (float) Math.toRadians(rotation[2]);
        return box;
    }

    // Citadel: 1.21 - setupAnim now takes EntityRenderState, not Entity with animation parameters
    // We keep the old logic in a separate method for animator compatibility
    @Override
    public void setupAnim(EntityRenderState state) {
        // Citadel: In 1.21, state doesn't have the same animation data as before
        // Call internal method with default values for now
        // This would need proper integration with the state object in a full implementation
        this.setupAnimInternal(null, 0, 0, 0, 0, 0);
    }
    
    // Citadel: Internal method to keep compatibility with existing animator logic
    private void setupAnimInternal(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float rotationYaw, float rotationPitch) {
        if (this.tabulaAnimator != null) {
            this.tabulaAnimator.setRotationAngles(this, entity, limbSwing, limbSwingAmount, ageInTicks, rotationYaw, rotationPitch, 1.0F);
        }
    }

    public AdvancedModelBox getCube(String name) {
        return this.cubes.get(name);
    }

    public AdvancedModelBox getCubeByIdentifier(String identifier) {
        return this.identifierMap.get(identifier);
    }

    public Map<String, AdvancedModelBox> getCubes() {
        return this.cubes;
    }

    @Override
    public Iterable<BasicModelPart> parts() {
        return ImmutableList.copyOf(rootBoxes);
    }

    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        return ImmutableList.copyOf(cubes.values());
    }


}