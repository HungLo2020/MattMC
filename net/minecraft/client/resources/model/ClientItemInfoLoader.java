package net.minecraft.client.resources.model;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.PlaceholderLookupProvider;
import net.minecraft.util.StrictJsonParser;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientItemInfoLoader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter LISTER = FileToIdConverter.json("items");

	public static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> scheduleLoad(ResourceManager resourceManager, Executor executor) {
		Frozen frozen = ClientRegistryLayer.createRegistryAccess().compositeAccess();
		return CompletableFuture.supplyAsync(() -> LISTER.listMatchingResources(resourceManager), executor)
			.thenCompose(
				map -> {
					List<CompletableFuture<ClientItemInfoLoader.PendingLoad>> list = new ArrayList(map.size());
					map.forEach(
						(resourceLocation, resource) -> list.add(
							CompletableFuture.supplyAsync(
								() -> {
									ResourceLocation resourceLocation2 = LISTER.fileToId(resourceLocation);

									try {
										Reader reader = resource.openAsReader();

										ClientItemInfoLoader.PendingLoad var8;
										try {
											PlaceholderLookupProvider placeholderLookupProvider = new PlaceholderLookupProvider(frozen);
											DynamicOps<JsonElement> dynamicOps = placeholderLookupProvider.createSerializationContext(JsonOps.INSTANCE);
											ClientItem clientItem = (ClientItem)ClientItem.CODEC
												.parse(dynamicOps, StrictJsonParser.parse(reader))
												.ifError(error -> LOGGER.error("Couldn't parse item model '{}' from pack '{}': {}", resourceLocation2, resource.sourcePackId(), error.message()))
												.result()
												.map(
													clientItemx -> placeholderLookupProvider.hasRegisteredPlaceholders()
														? clientItemx.withRegistrySwapper(placeholderLookupProvider.createSwapper())
														: clientItemx
												)
												.orElse(null);
											var8 = new ClientItemInfoLoader.PendingLoad(resourceLocation2, clientItem);
										} catch (Throwable var10) {
											if (reader != null) {
												try {
													reader.close();
												} catch (Throwable var9) {
													var10.addSuppressed(var9);
												}
											}

											throw var10;
										}

										if (reader != null) {
											reader.close();
										}

										return var8;
									} catch (Exception var11) {
										LOGGER.error("Failed to open item model {} from pack '{}'", resourceLocation, resource.sourcePackId(), var11);
										return new ClientItemInfoLoader.PendingLoad(resourceLocation2, null);
									}
								},
								executor
							)
						)
					);
					return Util.sequence(list).thenApply(listx -> {
						Map<ResourceLocation, ClientItem> mapx = new HashMap();

						for (ClientItemInfoLoader.PendingLoad pendingLoad : listx) {
							if (pendingLoad.clientItemInfo != null) {
								mapx.put(pendingLoad.id, pendingLoad.clientItemInfo);
							}
						}

						return new ClientItemInfoLoader.LoadedClientInfos(mapx);
					});
				}
			);
	}

	@Environment(EnvType.CLIENT)
	public record LoadedClientInfos(Map<ResourceLocation, ClientItem> contents) {
	}

	@Environment(EnvType.CLIENT)
	record PendingLoad(ResourceLocation id, @Nullable ClientItem clientItemInfo) {
	}
}
