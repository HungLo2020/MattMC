package net.minecraft.server.jsonrpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.EncodeJsonRpcException;
import net.minecraft.server.jsonrpc.methods.IllegalMethodDefinitionException;
import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;
import org.jetbrains.annotations.Nullable;

public interface IncomingRpcMethod {
	MethodInfo info();

	IncomingRpcMethod.Attributes attributes();

	JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement jsonElement, ClientInfo clientInfo);

	static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<IncomingRpcMethod.ParameterlessMethod<Result>> method(
		IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> parameterlessRpcMethodFunction, Codec<Result> codec
	) {
		return new IncomingRpcMethod.IncomingRpcMethodBuilder<>((methodInfo, attributes) -> {
			if (methodInfo.params().isPresent()) {
				throw new IllegalMethodDefinitionException("Method defined as not having parameters but is describing them");
			} else if (methodInfo.result().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method lacks result");
			} else {
				return new IncomingRpcMethod.ParameterlessMethod<>(methodInfo, attributes, codec, parameterlessRpcMethodFunction);
			}
		});
	}

	static <Params, Result> IncomingRpcMethod.IncomingRpcMethodBuilder<IncomingRpcMethod.Method<Params, Result>> method(
		IncomingRpcMethod.RpcMethodFunction<Params, Result> rpcMethodFunction, Codec<Params> codec, Codec<Result> codec2
	) {
		return new IncomingRpcMethod.IncomingRpcMethodBuilder<>((methodInfo, attributes) -> {
			if (methodInfo.params().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method defined as having parameters without describing them");
			} else if (methodInfo.result().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method lacks result");
			} else {
				return new IncomingRpcMethod.Method<>(methodInfo, attributes, codec, codec2, rpcMethodFunction);
			}
		});
	}

	static <Result> IncomingRpcMethod.IncomingRpcMethodBuilder<IncomingRpcMethod.ParameterlessMethod<Result>> method(
		Function<MinecraftApi, Result> function, Codec<Result> codec
	) {
		return new IncomingRpcMethod.IncomingRpcMethodBuilder<>((methodInfo, attributes) -> {
			if (methodInfo.params().isPresent()) {
				throw new IllegalMethodDefinitionException("Method defined as not having parameters but is describing them");
			} else if (methodInfo.result().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method lacks result");
			} else {
				return new IncomingRpcMethod.ParameterlessMethod<>(methodInfo, attributes, codec, (minecraftApi, clientInfo) -> (Result)function.apply(minecraftApi));
			}
		});
	}

	public record Attributes(boolean runOnMainThread, boolean discoverable) {
	}

	@FunctionalInterface
	public interface Factory<T extends IncomingRpcMethod> {
		T create(MethodInfo methodInfo, IncomingRpcMethod.Attributes attributes);
	}

	public static class IncomingRpcMethodBuilder<T extends IncomingRpcMethod> {
		private final IncomingRpcMethod.Factory<T> method;
		private String description = "";
		@Nullable
		private ParamInfo paramInfo;
		@Nullable
		private ResultInfo resultInfo;
		private boolean discoverable = true;
		private boolean runOnMainThread = true;

		public IncomingRpcMethodBuilder(IncomingRpcMethod.Factory<T> factory) {
			this.method = factory;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<T> description(String string) {
			this.description = string;
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<T> response(ResultInfo resultInfo) {
			this.resultInfo = resultInfo;
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<T> param(ParamInfo paramInfo) {
			this.paramInfo = paramInfo;
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<T> undiscoverable() {
			this.discoverable = false;
			return this;
		}

		public IncomingRpcMethod.IncomingRpcMethodBuilder<T> notOnMainThread() {
			this.runOnMainThread = false;
			return this;
		}

		public T build() {
			MethodInfo methodInfo = new MethodInfo(this.description, this.paramInfo, this.resultInfo);
			return this.method.create(methodInfo, new IncomingRpcMethod.Attributes(this.runOnMainThread, this.discoverable));
		}

		public T register(Registry<IncomingRpcMethod> registry, String string) {
			return this.register(registry, ResourceLocation.withDefaultNamespace(string));
		}

		private T register(Registry<IncomingRpcMethod> registry, ResourceLocation resourceLocation) {
			return Registry.register(registry, resourceLocation, this.build());
		}
	}

	public record Method<Params, Result>(
		MethodInfo info,
		IncomingRpcMethod.Attributes attributes,
		Codec<Params> paramsCodec,
		Codec<Result> resultCodec,
		IncomingRpcMethod.RpcMethodFunction<Params, Result> function
	) implements IncomingRpcMethod {
		@Override
		public JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement jsonElement, ClientInfo clientInfo) {
			if (jsonElement != null && (jsonElement.isJsonArray() || jsonElement.isJsonObject())) {
				if (this.info.params().isEmpty()) {
					throw new IllegalArgumentException("Method defined as having parameters without describing them");
				} else {
					JsonElement jsonElement3;
					if (jsonElement.isJsonObject()) {
						String string = ((ParamInfo)this.info.params().get()).name();
						JsonElement jsonElement2 = jsonElement.getAsJsonObject().get(string);
						if (jsonElement2 == null) {
							throw new InvalidParameterJsonRpcException(String.format(Locale.ROOT, "Params passed by-name, but expected param [%s] does not exist", string));
						}

						jsonElement3 = jsonElement2;
					} else {
						JsonArray jsonArray = jsonElement.getAsJsonArray();
						if (jsonArray.isEmpty() || jsonArray.size() > 1) {
							throw new InvalidParameterJsonRpcException("Expected exactly one element in the params array");
						}

						jsonElement3 = jsonArray.get(0);
					}

					Params object = this.paramsCodec.parse(JsonOps.INSTANCE, jsonElement3).getOrThrow(InvalidParameterJsonRpcException::new);
					Result object2 = this.function.apply(minecraftApi, object, clientInfo);
					return this.resultCodec.encodeStart(JsonOps.INSTANCE, object2).getOrThrow(EncodeJsonRpcException::new);
				}
			} else {
				throw new InvalidParameterJsonRpcException("Expected params as array or named");
			}
		}
	}

	public record ParameterlessMethod<Result>(
		MethodInfo info, IncomingRpcMethod.Attributes attributes, Codec<Result> resultCodec, IncomingRpcMethod.ParameterlessRpcMethodFunction<Result> supplier
	) implements IncomingRpcMethod {
		@Override
		public JsonElement apply(MinecraftApi minecraftApi, @Nullable JsonElement jsonElement, ClientInfo clientInfo) {
			if (jsonElement == null || jsonElement.isJsonArray() && jsonElement.getAsJsonArray().isEmpty()) {
				if (this.info.params().isPresent()) {
					throw new IllegalArgumentException("Method defined as not having parameters but is describing them");
				} else {
					Result object = this.supplier.apply(minecraftApi, clientInfo);
					return this.resultCodec.encodeStart(JsonOps.INSTANCE, object).getOrThrow(InvalidParameterJsonRpcException::new);
				}
			} else {
				throw new InvalidParameterJsonRpcException("Expected no params, or an empty array");
			}
		}
	}

	@FunctionalInterface
	public interface ParameterlessRpcMethodFunction<Result> {
		Result apply(MinecraftApi minecraftApi, ClientInfo clientInfo);
	}

	@FunctionalInterface
	public interface RpcMethodFunction<Params, Result> {
		Result apply(MinecraftApi minecraftApi, Params object, ClientInfo clientInfo);
	}
}
