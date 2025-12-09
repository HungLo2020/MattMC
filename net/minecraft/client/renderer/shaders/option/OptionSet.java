package net.minecraft.client.renderer.shaders.option;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;

/**
 * IRIS 1.21.9 OptionSet with Merged option support
 */
public class OptionSet {
private final ImmutableMap<String, MergedBooleanOption> booleanOptions;
private final ImmutableMap<String, MergedStringOption> stringOptions;

private OptionSet(ImmutableMap<String, MergedBooleanOption> booleanOptions,
  ImmutableMap<String, MergedStringOption> stringOptions) {
this.booleanOptions = booleanOptions;
this.stringOptions = stringOptions;
}

public ImmutableMap<String, MergedBooleanOption> getBooleanOptions() {
return booleanOptions;
}

public ImmutableMap<String, MergedStringOption> getStringOptions() {
return stringOptions;
}

public static Builder builder() {
		return new Builder();
	}

public static class Builder {
private final Map<String, MergedBooleanOption> booleanOptions = new HashMap<>();
private final Map<String, MergedStringOption> stringOptions = new HashMap<>();

public void addBooleanOption(OptionLocation location, BooleanOption option) {
MergedBooleanOption existing = booleanOptions.get(option.getName());
MergedBooleanOption newOption = new MergedBooleanOption(location, option);

if (existing != null) {
MergedBooleanOption merged = existing.merge(newOption);
if (merged == null) {
// Conflict - ignore ambiguous option
booleanOptions.remove(option.getName());
System.err.println("Warning: Ambiguous boolean option " + option.getName());
return;
}
booleanOptions.put(option.getName(), merged);
} else {
booleanOptions.put(option.getName(), newOption);
}
}

public void addStringOption(OptionLocation location, StringOption option) {
MergedStringOption existing = stringOptions.get(option.getName());
MergedStringOption newOption = new MergedStringOption(location, option);

if (existing != null) {
MergedStringOption merged = existing.merge(newOption);
if (merged == null) {
// Conflict - ignore ambiguous option
stringOptions.remove(option.getName());
System.err.println("Warning: Ambiguous string option " + option.getName());
return;
}
stringOptions.put(option.getName(), merged);
} else {
stringOptions.put(option.getName(), newOption);
}
}

public OptionSet build() {
return new OptionSet(
ImmutableMap.copyOf(booleanOptions),
ImmutableMap.copyOf(stringOptions)
);
}
}
}
