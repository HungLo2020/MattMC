/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.fabric.impl.resource.loader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.metadata.ResourceMetadataMap;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record PlaceholderResourcePack(PackType type, PackLocationInfo metadata) implements Pack {
	private static final Component DESCRIPTION_TEXT = Component.translatable("pack.description.modResources");

	public PackMetadataSection getMetadata() {
		return ModResourcePackUtil.getMetadataPack(
				SharedConstants.getGameVersion().packVersion(type),
				DESCRIPTION_TEXT
		);
	}

	@Nullable
	@Override
	public IoSupplier<InputStream> openRoot(String... segments) {
		if (segments.length > 0) {
			switch (segments[0]) {
			case "pack.mcmeta":
				return () -> {
					DataResult<JsonElement> result = PackMetadataSection.createCodec(type)
							.encodeStart(JsonOps.INSTANCE, getMetadata());
					String metadata = result.getOrThrow().toString();
					return IOUtils.toInputStream(metadata, StandardCharsets.UTF_8);
				};
			case "pack.png":
				return ModResourcePackUtil::getDefaultIcon;
			}
		}

		return null;
	}

	/**
	 * This pack has no actual contents.
	 */
	@Nullable
	@Override
	public IoSupplier<InputStream> open(PackType type, ResourceLocation id) {
		return null;
	}

	@Override
	public void findResources(PackType type, String namespace, String prefix, ResultConsumer consumer) {
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		return Collections.emptySet();
	}

	@Nullable
	@Override
	public <T> T parseMetadata(MetadataSectionType<T> metaReader) {
		return ResourceMetadataMap.of(PackMetadataSection.getSerializerFor(type), getMetadata()).get(metaReader);
	}

	@Override
	public PackLocationInfo getInfo() {
		return metadata;
	}

	@Override
	public String getId() {
		return ModResourcePackCreator.FABRIC;
	}

	@Override
	public void close() {
	}

	public record Factory(PackType type, PackLocationInfo metadata) implements Pack.ResourcesSupplier {
		@Override
		public Pack open(PackLocationInfo var1) {
			return new PlaceholderResourcePack(this.type, metadata);
		}

		@Override
		public Pack openWithOverlays(PackLocationInfo var1, Pack.Metadata metadata) {
			return open(var1);
		}
	}
}
