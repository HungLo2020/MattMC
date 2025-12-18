package frnsrc.Iris;

import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

import java.util.Map;

public record TagEntry(NamespacedId id, Map<String, String> propertyPredicates) implements Entry {
}
