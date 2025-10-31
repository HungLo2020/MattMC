package MattMC.resources;

/**
 * Represents a single variant in a blockstate JSON file.
 * 
 * Example:
 * { "model": "block/dirt", "x": 90, "y": 180, "weight": 400 }
 */
public class BlockStateVariant {
    private String model;
    private Integer x;
    private Integer y;
    private Integer weight;
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public Integer getX() {
        return x;
    }
    
    public void setX(Integer x) {
        this.x = x;
    }
    
    public Integer getY() {
        return y;
    }
    
    public void setY(Integer y) {
        this.y = y;
    }
    
    public Integer getWeight() {
        return weight;
    }
    
    public void setWeight(Integer weight) {
        this.weight = weight;
    }
}
