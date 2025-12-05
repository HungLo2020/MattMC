package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

public class JsonRPCUtils {
	public static final String JSON_RPC_VERSION = "2.0";
	public static final String OPEN_RPC_VERSION = "1.3.2";

	public static JsonObject createSuccessResult(JsonElement jsonElement, JsonElement jsonElement2) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("jsonrpc", "2.0");
		jsonObject.add("id", jsonElement);
		jsonObject.add("result", jsonElement2);
		return jsonObject;
	}

	public static JsonObject createRequest(@Nullable Integer integer, ResourceLocation resourceLocation, List<JsonElement> list) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("jsonrpc", "2.0");
		if (integer != null) {
			jsonObject.addProperty("id", integer);
		}

		jsonObject.addProperty("method", resourceLocation.toString());
		if (!list.isEmpty()) {
			JsonArray jsonArray = new JsonArray(list.size());

			for (JsonElement jsonElement : list) {
				jsonArray.add(jsonElement);
			}

			jsonObject.add("params", jsonArray);
		}

		return jsonObject;
	}

	public static JsonObject createError(JsonElement jsonElement, String string, int i, @Nullable String string2) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("jsonrpc", "2.0");
		jsonObject.add("id", jsonElement);
		JsonObject jsonObject2 = new JsonObject();
		jsonObject2.addProperty("code", i);
		jsonObject2.addProperty("message", string);
		if (string2 != null && !string2.isBlank()) {
			jsonObject2.addProperty("data", string2);
		}

		jsonObject.add("error", jsonObject2);
		return jsonObject;
	}

	@Nullable
	public static JsonElement getRequestId(JsonObject jsonObject) {
		return jsonObject.get("id");
	}

	@Nullable
	public static String getMethodName(JsonObject jsonObject) {
		return GsonHelper.getAsString(jsonObject, "method", null);
	}

	@Nullable
	public static JsonElement getParams(JsonObject jsonObject) {
		return jsonObject.get("params");
	}

	@Nullable
	public static JsonElement getResult(JsonObject jsonObject) {
		return jsonObject.get("result");
	}

	@Nullable
	public static JsonObject getError(JsonObject jsonObject) {
		return GsonHelper.getAsJsonObject(jsonObject, "error", null);
	}
}
