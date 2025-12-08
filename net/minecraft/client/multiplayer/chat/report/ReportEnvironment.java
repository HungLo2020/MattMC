package net.minecraft.client.multiplayer.chat.report;

import com.mojang.authlib.yggdrasil.request.AbuseReportRequest.ClientInfo;
import com.mojang.authlib.yggdrasil.request.AbuseReportRequest.RealmInfo;
import com.mojang.authlib.yggdrasil.request.AbuseReportRequest.ThirdPartyServerInfo;
import java.util.Locale;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record ReportEnvironment(String clientVersion, @Nullable ReportEnvironment.Server server) {
	public static ReportEnvironment local() {
		return create(null);
	}

	public static ReportEnvironment thirdParty(String string) {
		return create(new ReportEnvironment.Server.ThirdParty(string));
	}

	public static ReportEnvironment create(@Nullable ReportEnvironment.Server server) {
		return new ReportEnvironment(getClientVersion(), server);
	}

	public ClientInfo clientInfo() {
		return new ClientInfo(this.clientVersion, Locale.getDefault().toLanguageTag());
	}

	@Nullable
	public ThirdPartyServerInfo thirdPartyServerInfo() {
		return this.server instanceof ReportEnvironment.Server.ThirdParty thirdParty ? new ThirdPartyServerInfo(thirdParty.ip) : null;
	}

	@Nullable
	public RealmInfo realmInfo() {
		return null;
	}

	private static String getClientVersion() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(SharedConstants.getCurrentVersion().id());
		if (Minecraft.checkModStatus().shouldReportAsModified()) {
			stringBuilder.append(" (modded)");
		}

		return stringBuilder.toString();
	}

	@Environment(EnvType.CLIENT)
	public interface Server {
		@Environment(EnvType.CLIENT)
		public record ThirdParty(String ip) implements ReportEnvironment.Server {
		}
	}
}
