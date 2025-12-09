package net.minecraft.client.renderer.shaders.option;

import net.minecraft.client.renderer.shaders.pack.AbsolutePackPath;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class OptionSetTest {

@Test
public void testEmptyOptionSet() {
OptionSet optionSet = OptionSet.builder().build();
assertNotNull(optionSet);
assertTrue(optionSet.getBooleanOptions().isEmpty());
assertTrue(optionSet.getStringOptions().isEmpty());
}

@Test
public void testAddBooleanOption() {
BooleanOption option = new BooleanOption(
OptionType.DEFINE,
"TEST",
"Test option",
true
);

OptionLocation location = new OptionLocation(
AbsolutePackPath.fromAbsolutePath("/shaders/test.fsh"),
1
);

OptionSet.Builder builder = OptionSet.builder();
builder.addBooleanOption(location, option);
OptionSet optionSet = builder.build();

assertFalse(optionSet.getBooleanOptions().isEmpty());
assertTrue(optionSet.getBooleanOptions().containsKey("TEST"));
}

@Test
public void testAddStringOption() {
StringOption option = StringOption.create(
OptionType.DEFINE,
"QUALITY",
"Quality setting // [LOW MEDIUM HIGH]",
"MEDIUM"
);

OptionLocation location = new OptionLocation(
AbsolutePackPath.fromAbsolutePath("/shaders/test.fsh"),
1
);

OptionSet.Builder builder = OptionSet.builder();
builder.addStringOption(location, option);
OptionSet optionSet = builder.build();

assertFalse(optionSet.getStringOptions().isEmpty());
assertTrue(optionSet.getStringOptions().containsKey("QUALITY"));
}

@Test
public void testMixedOptions() {
BooleanOption boolOption = new BooleanOption(
OptionType.DEFINE,
"ENABLE",
null,
false
);

StringOption strOption = StringOption.create(
OptionType.CONST,
"MODE",
"// [A B C]",
"A"
);

OptionLocation location = new OptionLocation(
AbsolutePackPath.fromAbsolutePath("/shaders/test.fsh"),
1
);

OptionSet.Builder builder = OptionSet.builder();
builder.addBooleanOption(location, boolOption);
builder.addStringOption(location, strOption);
OptionSet optionSet = builder.build();

assertEquals(1, optionSet.getBooleanOptions().size());
assertEquals(1, optionSet.getStringOptions().size());
}
}
