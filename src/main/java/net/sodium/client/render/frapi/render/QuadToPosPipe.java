package net.sodium.client.render.frapi.render;

import java.util.function.Consumer;

import net.sodium.api.math.MatrixHelper;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;

public class QuadToPosPipe implements Consumer<QuadView> {
    private final Consumer<Vector3fc> posConsumer;
    private final Vector3f vec;
    public Matrix4fc matrix;

    public QuadToPosPipe(Consumer<Vector3fc> posConsumer, Vector3f vec) {
        this.posConsumer = posConsumer;
        this.vec = vec;
    }

    @Override
    public void accept(QuadView quad) {
        for (int i = 0; i < 4; i++) {
            quad.copyPos(i, vec);

            vec.x = MatrixHelper.transformPositionX(matrix, vec.x, vec.y, vec.z);
            vec.y = MatrixHelper.transformPositionY(matrix, vec.x, vec.y, vec.z);
            vec.z = MatrixHelper.transformPositionZ(matrix, vec.x, vec.y, vec.z);

            posConsumer.accept(vec);
        }
    }
}