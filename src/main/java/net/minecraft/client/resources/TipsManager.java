package net.minecraft.client.resources;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TipsManager extends SimplePreparableReloadListener<List<String>> {
	private static final ResourceLocation TIPS_LOCATION = ResourceLocation.withDefaultNamespace("texts/tips.txt");
	private static final RandomSource RANDOM = RandomSource.create();
	private final List<String> tips = Lists.<String>newArrayList();

	public TipsManager() {
	}

	protected List<String> prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		try {
			BufferedReader bufferedReader = resourceManager.openAsReader(TIPS_LOCATION);

			List var4;
			try {
				var4 = (List)bufferedReader.lines().map(String::trim).filter(string -> !string.isEmpty()).collect(Collectors.toList());
			} catch (Throwable var7) {
				if (bufferedReader != null) {
					try {
						bufferedReader.close();
					} catch (Throwable var6) {
						var7.addSuppressed(var6);
					}
				}

				throw var7;
			}

			if (bufferedReader != null) {
				bufferedReader.close();
			}

			return var4;
		} catch (IOException var8) {
			return Collections.emptyList();
		}
	}

	protected void apply(List<String> list, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		this.tips.clear();
		this.tips.addAll(list);
	}

	@Nullable
	public String getRandomTip() {
		if (this.tips.isEmpty()) {
			return null;
		} else {
			return this.tips.get(RANDOM.nextInt(this.tips.size()));
		}
	}
}
