package mattmc.client.renderer.block.model;

/**
 * Represents UV coordinates and rotation for a block face.
 * Based on Minecraft's net.minecraft.client.renderer.block.model.BlockFaceUV
 */
public class BlockFaceUV {
    public float[] uvs;
    public final int rotation;
    
    public BlockFaceUV(float[] uvs, int rotation) {
        this.uvs = uvs;
        this.rotation = rotation;
    }
    
    public float getU(int index) {
        if (this.uvs == null) {
            throw new NullPointerException("uvs");
        }
        int i = this.getShiftedIndex(index);
        return this.uvs[i != 0 && i != 1 ? 2 : 0];
    }
    
    public float getV(int index) {
        if (this.uvs == null) {
            throw new NullPointerException("uvs");
        }
        int i = this.getShiftedIndex(index);
        return this.uvs[i != 0 && i != 3 ? 3 : 1];
    }
    
    private int getShiftedIndex(int index) {
        return (index + this.rotation / 90) % 4;
    }
    
    public int getReverseIndex(int index) {
        return (index + 4 - this.rotation / 90) % 4;
    }
}
