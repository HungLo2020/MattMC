package frnsrc.sodium;

import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;

public interface BakedQuadView extends ModelQuadView {
    ModelQuadFacing getNormalFace();

    int getFaceNormal();

    boolean hasShade();

    boolean hasAO();
}
