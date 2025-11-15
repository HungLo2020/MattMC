package mattmc.client.resources.model;

import java.util.Map;

/**
 * Wrapper for custom item model format used in this project.
 * Handles the format:
 * {
 *   "model": {
 *     "type": "mattmc:model",
 *     "model": "mattmc:block/cobblestone"
 *   }
 * }
 */
public class ItemModelWrapper {
    private ModelReference model;
    
    public ModelReference getModel() {
        return model;
    }
    
    public void setModel(ModelReference model) {
        this.model = model;
    }
    
    public static class ModelReference {
        private String type;
        private String model;
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public String getModel() {
            return model;
        }
        
        public void setModel(String model) {
            this.model = model;
        }
    }
}
