package net.minecraft.server.jsonrpc.security;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;

@Sharable
public class AuthenticationHandler extends ChannelInboundHandlerAdapter {
	private final Logger LOGGER = LogUtils.getLogger();
	private static final AttributeKey<Boolean> AUTHENTICATED_KEY = AttributeKey.valueOf("authenticated");
	public static final String AUTH_HEADER = "Authorization";
	public static final String BEARER_PREFIX = "Bearer ";
	private final SecurityConfig securityConfig;

	public AuthenticationHandler(SecurityConfig securityConfig) {
		this.securityConfig = securityConfig;
	}

	@Override
	public void channelRead(ChannelHandlerContext channelHandlerContext, Object object) throws Exception {
		String string = this.getClientIp(channelHandlerContext);
		if (object instanceof HttpRequest httpRequest) {
			AuthenticationHandler.SecurityCheckResult securityCheckResult = this.performSecurityChecks(httpRequest);
			if (!securityCheckResult.isAllowed()) {
				this.LOGGER.debug("Authentication rejected for connection with ip {}: {}", string, securityCheckResult.getReason());
				channelHandlerContext.channel().attr(AUTHENTICATED_KEY).set(false);
				this.sendUnauthorizedResponse(channelHandlerContext, securityCheckResult.getReason());
				return;
			}

			channelHandlerContext.channel().attr(AUTHENTICATED_KEY).set(true);
		}

		Boolean boolean_ = channelHandlerContext.channel().attr(AUTHENTICATED_KEY).get();
		if (Boolean.TRUE.equals(boolean_)) {
			super.channelRead(channelHandlerContext, object);
		} else {
			this.LOGGER.debug("Dropping unauthenticated connection with ip {}", string);
			channelHandlerContext.close();
		}
	}

	private AuthenticationHandler.SecurityCheckResult performSecurityChecks(HttpRequest httpRequest) {
		return !this.validateAuthentication(httpRequest)
			? AuthenticationHandler.SecurityCheckResult.denied("Invalid or missing API key")
			: AuthenticationHandler.SecurityCheckResult.allowed();
	}

	private boolean validateAuthentication(HttpRequest httpRequest) {
		String string = httpRequest.headers().get("Authorization");
		if (string == null || string.trim().isEmpty()) {
			return false;
		} else if (string.startsWith("Bearer ")) {
			String string2 = string.substring("Bearer ".length()).trim();
			return this.isValidApiKey(string2);
		} else {
			return false;
		}
	}

	public boolean isValidApiKey(String string) {
		if (string != null && !string.isEmpty()) {
			byte[] bs = string.getBytes(StandardCharsets.UTF_8);
			byte[] cs = this.securityConfig.secretKey().getBytes(StandardCharsets.UTF_8);
			return MessageDigest.isEqual(bs, cs);
		} else {
			return false;
		}
	}

	private String getClientIp(ChannelHandlerContext channelHandlerContext) {
		InetSocketAddress inetSocketAddress = (InetSocketAddress)channelHandlerContext.channel().remoteAddress();
		return inetSocketAddress.getAddress().getHostAddress();
	}

	private void sendUnauthorizedResponse(ChannelHandlerContext channelHandlerContext, String string) {
		String string2 = "{\"error\":\"Unauthorized\",\"message\":\"" + string + "\"}";
		byte[] bs = string2.getBytes(StandardCharsets.UTF_8);
		DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(
			HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, Unpooled.wrappedBuffer(bs)
		);
		defaultFullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
		defaultFullHttpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, bs.length);
		defaultFullHttpResponse.headers().set(HttpHeaderNames.CONNECTION, "close");
		channelHandlerContext.writeAndFlush(defaultFullHttpResponse).addListener(future -> channelHandlerContext.close());
	}

	static class SecurityCheckResult {
		private final boolean allowed;
		private final String reason;

		private SecurityCheckResult(boolean bl, String string) {
			this.allowed = bl;
			this.reason = string;
		}

		public static AuthenticationHandler.SecurityCheckResult allowed() {
			return new AuthenticationHandler.SecurityCheckResult(true, null);
		}

		public static AuthenticationHandler.SecurityCheckResult denied(String string) {
			return new AuthenticationHandler.SecurityCheckResult(false, string);
		}

		public boolean isAllowed() {
			return this.allowed;
		}

		public String getReason() {
			return this.reason;
		}
	}
}
