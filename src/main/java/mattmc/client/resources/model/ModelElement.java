package mattmc.client.resources.model;

import java.util.List;

/**
 * Represents a model element (cuboid) in Minecraft's block model format.
 * Each element defines a box shape with UV-mapped faces.
 */
public class ModelElement {
    private List<Float> from;  // [x1, y1, z1] - starting corner (0-16 range)
    private List<Float> to;    // [x2, y2, z2] - ending corner (0-16 range)
    private java.util.Map<String, ElementFace> faces;  // Faces of this element (up, down, north, south, east, west)
    private ModelRotation rotation;  // Optional rotation of this element
    private Boolean shade;  // Whether to apply shading (default: true)
    
    public List<Float> getFrom() {
        return from;
    }
    
    public void setFrom(List<Float> from) {
        this.from = from;
    }
    
    public List<Float> getTo() {
        return to;
    }
    
    public void setTo(List<Float> to) {
        this.to = to;
    }
    
    public java.util.Map<String, ElementFace> getFaces() {
        return faces;
    }
    
    public void setFaces(java.util.Map<String, ElementFace> faces) {
        this.faces = faces;
    }
    
    public ModelRotation getRotation() {
        return rotation;
    }
    
    public void setRotation(ModelRotation rotation) {
        this.rotation = rotation;
    }
    
    public Boolean getShade() {
        return shade;
    }
    
    public void setShade(Boolean shade) {
        this.shade = shade;
    }
    
    /**
     * Represents a single face of a model element.
     */
    public static class ElementFace {
        private List<Float> uv;  // [u1, v1, u2, v2] UV coordinates (0-16 range)
        private String texture;  // Texture variable reference (e.g., "#side")
        private String cullface;  // Direction to cull against (up, down, north, south, east, west)
        private Integer rotation;  // Texture rotation in degrees (0, 90, 180, 270)
        private Integer tintindex;  // Tint index for this face
        
        public List<Float> getUv() {
            return uv;
        }
        
        public void setUv(List<Float> uv) {
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
        
        public Integer getRotation() {
            return rotation;
        }
        
        public void setRotation(Integer rotation) {
            this.rotation = rotation;
        }
        
        public Integer getTintindex() {
            return tintindex;
        }
        
        public void setTintindex(Integer tintindex) {
            this.tintindex = tintindex;
        }
    }
    
    /**
     * Represents rotation of a model element.
     */
    public static class ModelRotation {
        private List<Float> origin;  // [x, y, z] rotation origin point
        private String axis;  // Rotation axis (x, y, or z)
        private Float angle;  // Rotation angle (degrees)
        private Boolean rescale;  // Whether to rescale after rotation
        
        public List<Float> getOrigin() {
            return origin;
        }
        
        public void setOrigin(List<Float> origin) {
            this.origin = origin;
        }
        
        public String getAxis() {
            return axis;
        }
        
        public void setAxis(String axis) {
            this.axis = axis;
        }
        
        public Float getAngle() {
            return angle;
        }
        
        public void setAngle(Float angle) {
            this.angle = angle;
        }
        
        public Boolean getRescale() {
            return rescale;
        }
        
        public void setRescale(Boolean rescale) {
            this.rescale = rescale;
        }
    }
}
