package net.minecraft.server.jsonrpc;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.jsonrpc.api.MethodInfo;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.methods.IllegalMethodDefinitionException;
import org.jetbrains.annotations.Nullable;

public interface OutgoingRpcMethod<Params, Result> {
	String NOTIFICATION_PREFIX = "notification/";

	MethodInfo info();

	OutgoingRpcMethod.Attributes attributes();

	@Nullable
	default JsonElement encodeParams(Params object) {
		return null;
	}

	@Nullable
	default Result decodeResult(JsonElement jsonElement) {
		return null;
	}

	static OutgoingRpcMethod.OutgoingRpcMethodBuilder<OutgoingRpcMethod.ParmeterlessNotification> notification() {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>((methodInfo, attributes) -> {
			if (methodInfo.params().isPresent()) {
				throw new IllegalMethodDefinitionException("Method defined as not having parameters but is describing them");
			} else if (methodInfo.result().isPresent()) {
				throw new IllegalMethodDefinitionException("Method defined as not having result but is describing it");
			} else {
				return new OutgoingRpcMethod.ParmeterlessNotification(methodInfo, attributes);
			}
		});
	}

	static <Params> OutgoingRpcMethod.OutgoingRpcMethodBuilder<OutgoingRpcMethod.Notification<Params>> notification(Codec<Params> codec) {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>((methodInfo, attributes) -> {
			if (methodInfo.params().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method defined as having parameters without describing them");
			} else if (methodInfo.result().isPresent()) {
				throw new IllegalMethodDefinitionException("Method defined as not having result but is describing it");
			} else {
				return new OutgoingRpcMethod.Notification<>(methodInfo, attributes, codec);
			}
		});
	}

	static <Result> OutgoingRpcMethod.OutgoingRpcMethodBuilder<OutgoingRpcMethod.ParameterlessMethod<Result>> request(Codec<Result> codec) {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>((methodInfo, attributes) -> {
			if (methodInfo.params().isPresent()) {
				throw new IllegalMethodDefinitionException("Method defined as not having parameters but is describing them");
			} else if (methodInfo.result().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method lacks result");
			} else {
				return new OutgoingRpcMethod.ParameterlessMethod<>(methodInfo, attributes, codec);
			}
		});
	}

	static <Params, Result> OutgoingRpcMethod.OutgoingRpcMethodBuilder<OutgoingRpcMethod.Method<Params, Result>> request(Codec<Params> codec, Codec<Result> codec2) {
		return new OutgoingRpcMethod.OutgoingRpcMethodBuilder<>((methodInfo, attributes) -> {
			if (methodInfo.params().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method defined as having parameters without describing them");
			} else if (methodInfo.result().isEmpty()) {
				throw new IllegalMethodDefinitionException("Method lacks result");
			} else {
				return new OutgoingRpcMethod.Method<>(methodInfo, attributes, codec, codec2);
			}
		});
	}

	public record Attributes(boolean discoverable) {
	}

	@FunctionalInterface
	public interface Factory<T extends OutgoingRpcMethod<?, ?>> {
		T create(MethodInfo methodInfo, OutgoingRpcMethod.Attributes attributes);
	}

	public record Method<Params, Result>(MethodInfo info, OutgoingRpcMethod.Attributes attributes, Codec<Params> paramsCodec, Codec<Result> resultCodec)
		implements OutgoingRpcMethod<Params, Result> {
		@Nullable
		@Override
		public JsonElement encodeParams(Params object) {
			return this.paramsCodec.encodeStart(JsonOps.INSTANCE, object).getOrThrow();
		}

		@Override
		public Result decodeResult(JsonElement jsonElement) {
			return this.resultCodec.parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
		}
	}

	public record Notification<Params>(MethodInfo info, OutgoingRpcMethod.Attributes attributes, Codec<Params> paramsCodec)
		implements OutgoingRpcMethod<Params, Void> {
		@Nullable
		@Override
		public JsonElement encodeParams(Params object) {
			return this.paramsCodec.encodeStart(JsonOps.INSTANCE, object).getOrThrow();
		}
	}

	public static class OutgoingRpcMethodBuilder<T extends OutgoingRpcMethod<?, ?>> {
		public static final OutgoingRpcMethod.Attributes DEFAULT_ATTRIBUTES = new OutgoingRpcMethod.Attributes(true);
		private final OutgoingRpcMethod.Factory<T> method;
		private String description = "";
		@Nullable
		private ParamInfo paramInfo;
		@Nullable
		private ResultInfo resultInfo;

		public OutgoingRpcMethodBuilder(OutgoingRpcMethod.Factory<T> factory) {
			this.method = factory;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<T> description(String string) {
			this.description = string;
			return this;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<T> response(ResultInfo resultInfo) {
			this.resultInfo = resultInfo;
			return this;
		}

		public OutgoingRpcMethod.OutgoingRpcMethodBuilder<T> param(ParamInfo paramInfo) {
			this.paramInfo = paramInfo;
			return this;
		}

		private T build() {
			MethodInfo methodInfo = new MethodInfo(this.description, this.paramInfo, this.resultInfo);
			return this.method.create(methodInfo, DEFAULT_ATTRIBUTES);
		}

		public Holder.Reference<T> register(String string) {
			return this.register(ResourceLocation.withDefaultNamespace("notification/" + string));
		}

		private Holder.Reference<T> register(ResourceLocation resourceLocation) {
			return Registry.registerForHolder(BuiltInRegistries.OUTGOING_RPC_METHOD, resourceLocation, this.build());
		}
	}

	public record ParameterlessMethod<Result>(MethodInfo info, OutgoingRpcMethod.Attributes attributes, Codec<Result> resultCodec)
		implements OutgoingRpcMethod<Void, Result> {
		@Override
		public Result decodeResult(JsonElement jsonElement) {
			return this.resultCodec.parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
		}
	}

	public record ParmeterlessNotification(MethodInfo info, OutgoingRpcMethod.Attributes attributes) implements OutgoingRpcMethod<Void, Void> {
	}
}
