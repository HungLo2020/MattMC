package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.PermissionSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider.ElementSuggestionType;
import net.minecraft.commands.SharedSuggestionProvider.TextCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket.Action;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ClientSuggestionProvider implements PermissionSource, SharedSuggestionProvider {
	private final ClientPacketListener connection;
	private final Minecraft minecraft;
	private int pendingSuggestionsId = -1;
	@Nullable
	private CompletableFuture<Suggestions> pendingSuggestionsFuture;
	private final Set<String> customCompletionSuggestions = new HashSet();
	private final boolean allowsRestrictedCommands;

	public ClientSuggestionProvider(ClientPacketListener clientPacketListener, Minecraft minecraft, boolean bl) {
		this.connection = clientPacketListener;
		this.minecraft = minecraft;
		this.allowsRestrictedCommands = bl;
	}

	public Collection<String> getOnlinePlayerNames() {
		List<String> list = Lists.<String>newArrayList();

		for (PlayerInfo playerInfo : this.connection.getOnlinePlayers()) {
			list.add(playerInfo.getProfile().name());
		}

		return list;
	}

	public Collection<String> getCustomTabSugggestions() {
		if (this.customCompletionSuggestions.isEmpty()) {
			return this.getOnlinePlayerNames();
		} else {
			Set<String> set = new HashSet(this.getOnlinePlayerNames());
			set.addAll(this.customCompletionSuggestions);
			return set;
		}
	}

	public Collection<String> getSelectedEntities() {
		return (Collection<String>)(this.minecraft.hitResult != null && this.minecraft.hitResult.getType() == Type.ENTITY
			? Collections.singleton(((EntityHitResult)this.minecraft.hitResult).getEntity().getStringUUID())
			: Collections.emptyList());
	}

	public Collection<String> getAllTeams() {
		return this.connection.scoreboard().getTeamNames();
	}

	public Stream<ResourceLocation> getAvailableSounds() {
		return this.minecraft.getSoundManager().getAvailableSounds().stream();
	}

	public boolean hasPermission(int i) {
		return this.allowsRestrictedCommands || i == 0;
	}

	public boolean allowsSelectors() {
		return this.allowsRestrictedCommands;
	}

	public CompletableFuture<Suggestions> suggestRegistryElements(
		ResourceKey<? extends Registry<?>> resourceKey,
		ElementSuggestionType elementSuggestionType,
		SuggestionsBuilder suggestionsBuilder,
		CommandContext<?> commandContext
	) {
		return (CompletableFuture<Suggestions>)this.registryAccess().lookup(resourceKey).map(registry -> {
			this.suggestRegistryElements(registry, elementSuggestionType, suggestionsBuilder);
			return suggestionsBuilder.buildFuture();
		}).orElseGet(() -> this.customSuggestion(commandContext));
	}

	public CompletableFuture<Suggestions> customSuggestion(CommandContext<?> commandContext) {
		if (this.pendingSuggestionsFuture != null) {
			this.pendingSuggestionsFuture.cancel(false);
		}

		this.pendingSuggestionsFuture = new CompletableFuture();
		int i = ++this.pendingSuggestionsId;
		this.connection.send(new ServerboundCommandSuggestionPacket(i, commandContext.getInput()));
		return this.pendingSuggestionsFuture;
	}

	private static String prettyPrint(double d) {
		return String.format(Locale.ROOT, "%.2f", d);
	}

	private static String prettyPrint(int i) {
		return Integer.toString(i);
	}

	public Collection<TextCoordinates> getRelevantCoordinates() {
		HitResult hitResult = this.minecraft.hitResult;
		if (hitResult != null && hitResult.getType() == Type.BLOCK) {
			BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
			return Collections.singleton(new TextCoordinates(prettyPrint(blockPos.getX()), prettyPrint(blockPos.getY()), prettyPrint(blockPos.getZ())));
		} else {
			return SharedSuggestionProvider.super.getRelevantCoordinates();
		}
	}

	public Collection<TextCoordinates> getAbsoluteCoordinates() {
		HitResult hitResult = this.minecraft.hitResult;
		if (hitResult != null && hitResult.getType() == Type.BLOCK) {
			Vec3 vec3 = hitResult.getLocation();
			return Collections.singleton(new TextCoordinates(prettyPrint(vec3.x), prettyPrint(vec3.y), prettyPrint(vec3.z)));
		} else {
			return SharedSuggestionProvider.super.getAbsoluteCoordinates();
		}
	}

	public Set<ResourceKey<Level>> levels() {
		return this.connection.levels();
	}

	public RegistryAccess registryAccess() {
		return this.connection.registryAccess();
	}

	public FeatureFlagSet enabledFeatures() {
		return this.connection.enabledFeatures();
	}

	public void completeCustomSuggestions(int i, Suggestions suggestions) {
		if (i == this.pendingSuggestionsId) {
			this.pendingSuggestionsFuture.complete(suggestions);
			this.pendingSuggestionsFuture = null;
			this.pendingSuggestionsId = -1;
		}
	}

	public void modifyCustomCompletions(Action action, List<String> list) {
		switch (action) {
			case ADD:
				this.customCompletionSuggestions.addAll(list);
				break;
			case REMOVE:
				list.forEach(this.customCompletionSuggestions::remove);
				break;
			case SET:
				this.customCompletionSuggestions.clear();
				this.customCompletionSuggestions.addAll(list);
		}
	}

	public boolean allowsRestrictedCommands() {
		return this.allowsRestrictedCommands;
	}
}
