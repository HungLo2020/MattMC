package mattmc.client.resources.model;

import java.util.Map;

/**
 * Represents a single element in a block model.
 * Elements define cuboid shapes with textures on each face.
 * Similar to Minecraft's model element format.
 * 
 * Example:
 * {
 *   "from": [0, 0, 0],
 *   "to": [16, 8, 16],
 *   "faces": {
 *     "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
 *     "up": {"uv": [0, 0, 16, 16], "texture": "#top"}
 *   }
 * }
 */
public class ModelElement {
    private float[] from; // [x, y, z] in pixels (0-16)
    private float[] to;   // [x, y, z] in pixels (0-16)
    private Map<String, ElementFace> faces; // Map of face direction to ElementFace
    
    public float[] getFrom() {
        return from;
    }
    
    public void setFrom(float[] from) {
        this.from = from;
    }
    
    public float[] getTo() {
        return to;
    }
    
    public void setTo(float[] to) {
        this.to = to;
    }
    
    public Map<String, ElementFace> getFaces() {
        return faces;
    }
    
    public void setFaces(Map<String, ElementFace> faces) {
        this.faces = faces;
    }
    
    /**
     * Get a face by direction (down, up, north, south, west, east).
     */
    public ElementFace getFace(String direction) {
        return faces != null ? faces.get(direction) : null;
    }
    
    /**
     * Represents a single face of a model element.
     */
    public static class ElementFace {
        private float[] uv;      // [u0, v0, u1, v1] in pixels (0-16)
        private String texture;  // Texture reference (e.g., "#side")
        private String cullface; // Optional cullface direction
        
        public float[] getUv() {
            return uv;
        }
        
        public void setUv(float[] uv) {
            this.uv = uv;
        }
        
        public String getTexture() {
            return texture;
        }
        
        public void setTexture(String texture) {
            this.texture = texture;
        }
        
        public String getCullface() {
            return cullface;
        }
        
        public void setCullface(String cullface) {
            this.cullface = cullface;
        }
    }
}
