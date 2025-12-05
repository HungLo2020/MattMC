package net.minecraft.server.jsonrpc;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.ReadTimeoutException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import net.minecraft.server.jsonrpc.methods.EncodeJsonRpcException;
import net.minecraft.server.jsonrpc.methods.InvalidParameterJsonRpcException;
import net.minecraft.server.jsonrpc.methods.InvalidRequestJsonRpcException;
import net.minecraft.server.jsonrpc.methods.MethodNotFoundJsonRpcException;
import net.minecraft.server.jsonrpc.methods.RemoteRpcErrorException;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class Connection extends SimpleChannelInboundHandler<JsonElement> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final AtomicInteger CONNECTION_ID_COUNTER = new AtomicInteger(0);
	private final JsonRpcLogger jsonRpcLogger;
	private final ClientInfo clientInfo;
	private final ManagementServer managementServer;
	private final Channel channel;
	private final MinecraftApi minecraftApi;
	private final AtomicInteger transactionId = new AtomicInteger();
	private final Int2ObjectMap<PendingRpcRequest<?>> pendingRequests = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());

	public Connection(Channel channel, ManagementServer managementServer, MinecraftApi minecraftApi, JsonRpcLogger jsonRpcLogger) {
		this.clientInfo = ClientInfo.of(CONNECTION_ID_COUNTER.incrementAndGet());
		this.managementServer = managementServer;
		this.minecraftApi = minecraftApi;
		this.channel = channel;
		this.jsonRpcLogger = jsonRpcLogger;
	}

	public void tick() {
		long l = Util.getMillis();
		this.pendingRequests
			.int2ObjectEntrySet()
			.removeIf(
				entry -> {
					boolean bl = ((PendingRpcRequest)entry.getValue()).timedOut(l);
					if (bl) {
						((PendingRpcRequest)entry.getValue())
							.resultFuture()
							.completeExceptionally(
								new ReadTimeoutException("RPC method " + ((PendingRpcRequest)entry.getValue()).method().key().location() + " timed out waiting for response")
							);
					}

					return bl;
				}
			);
	}

	@Override
	public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
		this.jsonRpcLogger.log(this.clientInfo, "Management connection opened for {}", this.channel.remoteAddress());
		super.channelActive(channelHandlerContext);
		this.managementServer.onConnected(this);
	}

	@Override
	public void channelInactive(ChannelHandlerContext channelHandlerContext) throws Exception {
		this.jsonRpcLogger.log(this.clientInfo, "Management connection closed for {}", this.channel.remoteAddress());
		super.channelInactive(channelHandlerContext);
		this.managementServer.onDisconnected(this);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
		if (throwable.getCause() instanceof JsonParseException) {
			this.channel.writeAndFlush(JsonRPCErrors.PARSE_ERROR.createWithUnknownId(throwable.getMessage()));
		} else {
			super.exceptionCaught(channelHandlerContext, throwable);
			this.channel.close().awaitUninterruptibly();
		}
	}

	protected void channelRead0(ChannelHandlerContext channelHandlerContext, JsonElement jsonElement) {
		if (jsonElement.isJsonObject()) {
			JsonObject jsonObject = this.handleJsonObject(jsonElement.getAsJsonObject());
			if (jsonObject != null) {
				this.channel.writeAndFlush(jsonObject);
			}
		} else if (jsonElement.isJsonArray()) {
			this.channel.writeAndFlush(this.handleBatchRequest(jsonElement.getAsJsonArray().asList()));
		} else {
			this.channel.writeAndFlush(JsonRPCErrors.INVALID_REQUEST.createWithUnknownId(null));
		}
	}

	private JsonArray handleBatchRequest(List<JsonElement> list) {
		JsonArray jsonArray = new JsonArray();
		list.stream().map(jsonElement -> this.handleJsonObject(jsonElement.getAsJsonObject())).filter(Objects::nonNull).forEach(jsonArray::add);
		return jsonArray;
	}

	public void sendNotification(Holder.Reference<? extends OutgoingRpcMethod<Void, ?>> reference) {
		this.sendRequest(reference, null, false);
	}

	public <Params> void sendNotification(Holder.Reference<? extends OutgoingRpcMethod<Params, ?>> reference, Params object) {
		this.sendRequest(reference, object, false);
	}

	public <Result> CompletableFuture<Result> sendRequest(Holder.Reference<? extends OutgoingRpcMethod<Void, Result>> reference) {
		return this.sendRequest(reference, null, true);
	}

	public <Params, Result> CompletableFuture<Result> sendRequest(Holder.Reference<? extends OutgoingRpcMethod<Params, Result>> reference, Params object) {
		return this.sendRequest(reference, object, true);
	}

	@Nullable
	@Contract("_,_,false->null;_,_,true->!null")
	private <Params, Result> CompletableFuture<Result> sendRequest(
		Holder.Reference<? extends OutgoingRpcMethod<Params, ? extends Result>> reference, @Nullable Params object, boolean bl
	) {
		List<JsonElement> list = object != null ? List.of((JsonElement)Objects.requireNonNull(reference.value().encodeParams(object))) : List.of();
		if (bl) {
			CompletableFuture<Result> completableFuture = new CompletableFuture();
			int i = this.transactionId.incrementAndGet();
			long l = Util.timeSource.get(TimeUnit.MILLISECONDS);
			this.pendingRequests.put(i, new PendingRpcRequest<>(reference, completableFuture, l + 5000L));
			this.channel.writeAndFlush(JsonRPCUtils.createRequest(i, reference.key().location(), list));
			return completableFuture;
		} else {
			this.channel.writeAndFlush(JsonRPCUtils.createRequest(null, reference.key().location(), list));
			return null;
		}
	}

	@Nullable
	@VisibleForTesting
	JsonObject handleJsonObject(JsonObject jsonObject) {
		try {
			JsonElement jsonElement = JsonRPCUtils.getRequestId(jsonObject);
			String string = JsonRPCUtils.getMethodName(jsonObject);
			JsonElement jsonElement2 = JsonRPCUtils.getResult(jsonObject);
			JsonElement jsonElement3 = JsonRPCUtils.getParams(jsonObject);
			JsonObject jsonObject2 = JsonRPCUtils.getError(jsonObject);
			if (string != null && jsonElement2 == null && jsonObject2 == null) {
				return jsonElement != null && !isValidRequestId(jsonElement)
					? JsonRPCErrors.INVALID_REQUEST.createWithUnknownId("Invalid request id - only String, Number and NULL supported")
					: this.handleIncomingRequest(jsonElement, string, jsonElement3);
			} else if (string == null && jsonElement2 != null && jsonObject2 == null && jsonElement != null) {
				if (isValidResponseId(jsonElement)) {
					this.handleRequestResponse(jsonElement.getAsInt(), jsonElement2);
				} else {
					LOGGER.warn("Received respose {} with id {} we did not request", jsonElement2, jsonElement);
				}

				return null;
			} else {
				return string == null && jsonElement2 == null && jsonObject2 != null
					? this.handleError(jsonElement, jsonObject2)
					: JsonRPCErrors.INVALID_REQUEST.createWithoutData((JsonElement)Objects.requireNonNullElse(jsonElement, JsonNull.INSTANCE));
			}
		} catch (Exception var7) {
			LOGGER.error("Error while handling rpc request", (Throwable)var7);
			return JsonRPCErrors.INTERNAL_ERROR.createWithUnknownId("Unknown error handling request - check server logs for stack trace");
		}
	}

	private static boolean isValidRequestId(JsonElement jsonElement) {
		return jsonElement.isJsonNull() || GsonHelper.isNumberValue(jsonElement) || GsonHelper.isStringValue(jsonElement);
	}

	private static boolean isValidResponseId(JsonElement jsonElement) {
		return GsonHelper.isNumberValue(jsonElement);
	}

	@Nullable
	private JsonObject handleIncomingRequest(@Nullable JsonElement jsonElement, String string, @Nullable JsonElement jsonElement2) {
		boolean bl = jsonElement != null;

		try {
			JsonElement jsonElement3 = this.dispatchIncomingRequest(string, jsonElement2);
			return jsonElement3 != null && bl ? JsonRPCUtils.createSuccessResult(jsonElement, jsonElement3) : null;
		} catch (InvalidParameterJsonRpcException var6) {
			LOGGER.debug("Invalid parameter invocation {}: {}, {}", string, jsonElement2, var6.getMessage());
			return bl ? JsonRPCErrors.INVALID_PARAMS.create(jsonElement, var6.getMessage()) : null;
		} catch (EncodeJsonRpcException var7) {
			LOGGER.error("Failed to encode json rpc response {}: {}", string, var7.getMessage());
			return bl ? JsonRPCErrors.INTERNAL_ERROR.create(jsonElement, var7.getMessage()) : null;
		} catch (InvalidRequestJsonRpcException var8) {
			return bl ? JsonRPCErrors.INVALID_REQUEST.create(jsonElement, var8.getMessage()) : null;
		} catch (MethodNotFoundJsonRpcException var9) {
			return bl ? JsonRPCErrors.METHOD_NOT_FOUND.create(jsonElement, var9.getMessage()) : null;
		} catch (Exception var10) {
			LOGGER.error("Error while dispatching rpc method {}", string, var10);
			return bl ? JsonRPCErrors.INTERNAL_ERROR.createWithoutData(jsonElement) : null;
		}
	}

	@Nullable
	public JsonElement dispatchIncomingRequest(String string, @Nullable JsonElement jsonElement) {
		ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
		if (resourceLocation == null) {
			throw new InvalidRequestJsonRpcException("Failed to parse method value: " + string);
		} else {
			Optional<IncomingRpcMethod> optional = BuiltInRegistries.INCOMING_RPC_METHOD.getOptional(resourceLocation);
			if (optional.isEmpty()) {
				throw new MethodNotFoundJsonRpcException("Method not found: " + string);
			} else if (((IncomingRpcMethod)optional.get()).attributes().runOnMainThread()) {
				try {
					return (JsonElement)this.minecraftApi
						.submit((Supplier)(() -> ((IncomingRpcMethod)optional.get()).apply(this.minecraftApi, jsonElement, this.clientInfo)))
						.join();
				} catch (CompletionException var8) {
					if (var8.getCause() instanceof RuntimeException runtimeException) {
						throw runtimeException;
					} else {
						throw var8;
					}
				}
			} else {
				return ((IncomingRpcMethod)optional.get()).apply(this.minecraftApi, jsonElement, this.clientInfo);
			}
		}
	}

	private void handleRequestResponse(int i, JsonElement jsonElement) {
		PendingRpcRequest<?> pendingRpcRequest = this.pendingRequests.remove(i);
		if (pendingRpcRequest == null) {
			LOGGER.warn("Received unknown response (id: {}): {}", i, jsonElement);
		} else {
			pendingRpcRequest.accept(jsonElement);
		}
	}

	@Nullable
	private JsonObject handleError(@Nullable JsonElement jsonElement, JsonObject jsonObject) {
		if (jsonElement != null && isValidResponseId(jsonElement)) {
			PendingRpcRequest<?> pendingRpcRequest = this.pendingRequests.remove(jsonElement.getAsInt());
			if (pendingRpcRequest != null) {
				pendingRpcRequest.resultFuture().completeExceptionally(new RemoteRpcErrorException(jsonElement, jsonObject));
			}
		}

		LOGGER.error("Received error (id: {}): {}", jsonElement, jsonObject);
		return null;
	}
}
