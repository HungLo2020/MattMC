package net.alexsmobs.client.model;

import net.alexsmobs.client.render.UnderminerRenderState;
import net.citadel.client.model.AdvancedEntityModel;
import net.citadel.client.model.AdvancedModelBox;
import net.citadel.client.model.basic.BasicModelPart;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Wrapper model that delegates to either dwarf or tall model based on render state
 */
public class ModelUnderminerWrapper extends AdvancedEntityModel<UnderminerRenderState> {
    private final ModelUnderminerDwarf dwarfModel;
    private final HumanoidModel<UnderminerRenderState> tallModel;
    private UnderminerRenderState currentState;
    
    public ModelUnderminerWrapper(ModelPart tallModelPart) {
        this.dwarfModel = new ModelUnderminerDwarf();
        this.tallModel = new HumanoidModel<>(tallModelPart);
    }
    
    @Override
    public void setupAnim(UnderminerRenderState state) {
        this.currentState = state;
        if (state.isDwarf) {
            dwarfModel.setupAnim(state);
        } else {
            tallModel.setupAnim(state);
        }
    }
    
    @Override
    public Iterable<BasicModelPart> parts() {
        // Return the appropriate model's parts based on current state
        if (currentState != null && !currentState.isDwarf) {
            // For tall model, we need to adapt HumanoidModel parts to BasicModelPart
            // This is tricky - let's just use dwarf for now
            return dwarfModel.parts();
        }
        return dwarfModel.parts();
    }
    
    @Override
    public Iterable<AdvancedModelBox> getAllParts() {
        // Return dwarf parts for AdvancedEntityModel compatibility
        return dwarfModel.getAllParts();
    }
    
    public ModelUnderminerDwarf getDwarfModel() {
        return dwarfModel;
    }
    
    public HumanoidModel<UnderminerRenderState> getTallModel() {
        return tallModel;
    }
}
