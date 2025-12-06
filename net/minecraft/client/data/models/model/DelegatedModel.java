package net.minecraft.client.data.models.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public class DelegatedModel implements ModelInstance {
	private final ResourceLocation parent;

	public DelegatedModel(ResourceLocation resourceLocation) {
		this.parent = resourceLocation;
	}

	public JsonElement get() {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("parent", this.parent.toString());
		return jsonObject;
	}
}
