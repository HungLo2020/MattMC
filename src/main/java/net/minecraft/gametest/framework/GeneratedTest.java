package net.minecraft.gametest.framework;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public record GeneratedTest(
	Map<ResourceLocation, TestData<ResourceKey<TestEnvironmentDefinition>>> tests,
	ResourceKey<Consumer<GameTestHelper>> functionKey,
	Consumer<GameTestHelper> function
) {
	public GeneratedTest(
		Map<ResourceLocation, TestData<ResourceKey<TestEnvironmentDefinition>>> map, ResourceLocation resourceLocation, Consumer<GameTestHelper> consumer
	) {
		this(map, ResourceKey.create(Registries.TEST_FUNCTION, resourceLocation), consumer);
	}

	public GeneratedTest(ResourceLocation resourceLocation, TestData<ResourceKey<TestEnvironmentDefinition>> testData, Consumer<GameTestHelper> consumer) {
		this(Map.of(resourceLocation, testData), resourceLocation, consumer);
	}
}
