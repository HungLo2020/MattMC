package frnsrc.sodium;

import org.joml.Vector3dc;
import org.joml.Vector3fc;

public interface CombinedCameraPos {
    Vector3fc getRelativeCameraPos();

    Vector3dc getAbsoluteCameraPos();
}
