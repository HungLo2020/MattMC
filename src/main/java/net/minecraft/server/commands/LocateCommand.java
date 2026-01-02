package net.minecraft.server.commands;

import com.google.common.base.Stopwatch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

public class LocateCommand {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final DynamicCommandExceptionType ERROR_STRUCTURE_NOT_FOUND = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.locate.structure.not_found", object)
	);
	private static final DynamicCommandExceptionType ERROR_STRUCTURE_INVALID = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.locate.structure.invalid", object)
	);
	private static final DynamicCommandExceptionType ERROR_BIOME_NOT_FOUND = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.locate.biome.not_found", object)
	);
	private static final DynamicCommandExceptionType ERROR_POI_NOT_FOUND = new DynamicCommandExceptionType(
		object -> Component.translatableEscape("commands.locate.poi.not_found", object)
	);
	private static final int MAX_STRUCTURE_SEARCH_RADIUS = 100;
	private static final int MAX_BIOME_SEARCH_RADIUS = 6400;
	private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
	private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;
	private static final int POI_SEARCH_RADIUS = 256;

	public static void register(CommandDispatcher<CommandSourceStack> commandDispatcher, CommandBuildContext commandBuildContext) {
		commandDispatcher.register(
			Commands.literal("locate")
				.requires(Commands.hasPermission(2))
				.then(
					Commands.literal("structure")
						.then(
							Commands.argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE))
								.executes(
									commandContext -> locateStructure(
										commandContext.getSource(), ResourceOrTagKeyArgument.getResourceOrTagKey(commandContext, "structure", Registries.STRUCTURE, ERROR_STRUCTURE_INVALID)
									)
								)
						)
				)
				.then(
					Commands.literal("biome")
						.then(
							Commands.argument("biome", ResourceOrTagArgument.resourceOrTag(commandBuildContext, Registries.BIOME))
								.executes(commandContext -> locateBiome(commandContext.getSource(), ResourceOrTagArgument.getResourceOrTag(commandContext, "biome", Registries.BIOME)))
						)
				)
				.then(
					Commands.literal("poi")
						.then(
							Commands.argument("poi", ResourceOrTagArgument.resourceOrTag(commandBuildContext, Registries.POINT_OF_INTEREST_TYPE))
								.executes(
									commandContext -> locatePoi(
										commandContext.getSource(), ResourceOrTagArgument.getResourceOrTag(commandContext, "poi", Registries.POINT_OF_INTEREST_TYPE)
									)
								)
						)
				)
		);
	}

	private static Optional<? extends HolderSet.ListBacked<Structure>> getHolders(ResourceOrTagKeyArgument.Result<Structure> result, Registry<Structure> registry) {
		return result.unwrap().map(resourceKey -> registry.get(resourceKey).map(holder -> HolderSet.direct(holder)), registry::get);
	}

	private static int locateStructure(CommandSourceStack commandSourceStack, ResourceOrTagKeyArgument.Result<Structure> result) throws CommandSyntaxException {
		Registry<Structure> registry = commandSourceStack.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		HolderSet<Structure> holderSet = (HolderSet<Structure>)getHolders(result, registry).orElseThrow(() -> ERROR_STRUCTURE_INVALID.create(result.asPrintable()));
		BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
		ServerLevel serverLevel = commandSourceStack.getLevel();
		Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
		Pair<BlockPos, Holder<Structure>> pair = serverLevel.getChunkSource().getGenerator().findNearestMapStructure(serverLevel, holderSet, blockPos, 100, false);
		stopwatch.stop();
		if (pair == null) {
			throw ERROR_STRUCTURE_NOT_FOUND.create(result.asPrintable());
		} else {
			return showLocateResult(commandSourceStack, result, blockPos, pair, "commands.locate.structure.success", false, stopwatch.elapsed());
		}
	}

	private static int locateBiome(CommandSourceStack commandSourceStack, ResourceOrTagArgument.Result<Biome> result) throws CommandSyntaxException {
		BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
		Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
		Pair<BlockPos, Holder<Biome>> pair = commandSourceStack.getLevel().findClosestBiome3d(result, blockPos, 6400, 32, 64);
		stopwatch.stop();
		if (pair == null) {
			throw ERROR_BIOME_NOT_FOUND.create(result.asPrintable());
		} else {
			return showLocateResult(commandSourceStack, result, blockPos, pair, "commands.locate.biome.success", true, stopwatch.elapsed());
		}
	}

	private static int locatePoi(CommandSourceStack commandSourceStack, ResourceOrTagArgument.Result<PoiType> result) throws CommandSyntaxException {
		BlockPos blockPos = BlockPos.containing(commandSourceStack.getPosition());
		ServerLevel serverLevel = commandSourceStack.getLevel();
		Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
		Optional<Pair<Holder<PoiType>, BlockPos>> optional = serverLevel.getPoiManager().findClosestWithType(result, blockPos, 256, PoiManager.Occupancy.ANY);
		stopwatch.stop();
		if (optional.isEmpty()) {
			throw ERROR_POI_NOT_FOUND.create(result.asPrintable());
		} else {
			return showLocateResult(commandSourceStack, result, blockPos, ((Pair)optional.get()).swap(), "commands.locate.poi.success", false, stopwatch.elapsed());
		}
	}

	public static int showLocateResult(
		CommandSourceStack commandSourceStack,
		ResourceOrTagArgument.Result<?> result,
		BlockPos blockPos,
		Pair<BlockPos, ? extends Holder<?>> pair,
		String string,
		boolean bl,
		Duration duration
	) {
		String string2 = result.unwrap().map(reference -> result.asPrintable(), named -> result.asPrintable() + " (" + pair.getSecond().getRegisteredName() + ")");
		return showLocateResult(commandSourceStack, blockPos, pair, string, bl, string2, duration);
	}

	public static int showLocateResult(
		CommandSourceStack commandSourceStack,
		ResourceOrTagKeyArgument.Result<?> result,
		BlockPos blockPos,
		Pair<BlockPos, ? extends Holder<?>> pair,
		String string,
		boolean bl,
		Duration duration
	) {
		String string2 = result.unwrap()
			.map(resourceKey -> resourceKey.location().toString(), tagKey -> "#" + tagKey.location() + " (" + pair.getSecond().getRegisteredName() + ")");
		return showLocateResult(commandSourceStack, blockPos, pair, string, bl, string2, duration);
	}

	private static int showLocateResult(
		CommandSourceStack commandSourceStack,
		BlockPos blockPos,
		Pair<BlockPos, ? extends Holder<?>> pair,
		String string,
		boolean bl,
		String string2,
		Duration duration
	) {
		BlockPos blockPos2 = pair.getFirst();
		int i = bl ? Mth.floor(Mth.sqrt((float)blockPos.distSqr(blockPos2))) : Mth.floor(dist(blockPos.getX(), blockPos.getZ(), blockPos2.getX(), blockPos2.getZ()));
		String string3 = bl ? String.valueOf(blockPos2.getY()) : "~";
		
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Starting to create styled component for coordinates");
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Coordinates - X: {}, Y: {}, Z: {}", blockPos2.getX(), string3, blockPos2.getZ());
		
		// Create the click event
		ClickEvent.SuggestCommand clickEvent = new ClickEvent.SuggestCommand("/tp @s " + blockPos2.getX() + " " + string3 + " " + blockPos2.getZ());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Created ClickEvent.SuggestCommand with command: '{}'", clickEvent.command());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: ClickEvent action type: {}", clickEvent.action());
		
		// Create the hover event
		Component hoverText = Component.translatable("chat.coordinates.tooltip");
		HoverEvent.ShowText hoverEvent = new HoverEvent.ShowText(hoverText);
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Created HoverEvent.ShowText with text: '{}'", hoverText.getString());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: HoverEvent action type: {}", hoverEvent.action());
		
		// Create the base coordinate component
		Component coordinateComponent = Component.translatable("chat.coordinates", blockPos2.getX(), string3, blockPos2.getZ());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Created base coordinate component: '{}'", coordinateComponent.getString());
		
		// Wrap in square brackets
		MutableComponent wrappedComponent = ComponentUtils.wrapInSquareBrackets(coordinateComponent);
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Wrapped component in square brackets: '{}'", wrappedComponent.getString());
		
		// Apply style with color, click event, and hover event
		MutableComponent component = wrappedComponent.withStyle(
			style -> {
				LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Applying style - Input style: {}", style);
				Style styledResult = style.withColor(ChatFormatting.GREEN);
				LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: After withColor(GREEN): {}", styledResult);
				styledResult = styledResult.withClickEvent(clickEvent);
				LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: After withClickEvent: {}", styledResult);
				styledResult = styledResult.withHoverEvent(hoverEvent);
				LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: After withHoverEvent: {}", styledResult);
				return styledResult;
			}
		);
		
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Final styled component: '{}'", component.getString());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Final styled component style: {}", component.getStyle());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Final style color: {}", component.getStyle().getColor());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Final style clickEvent: {}", component.getStyle().getClickEvent());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Final style hoverEvent: {}", component.getStyle().getHoverEvent());
		
		// Create the full message - DON'T resolve it, send the raw translatable component
		// The client will resolve it and should preserve styles
		Component fullMessage = Component.translatable(string, string2, component, i);
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Full message to send: '{}'", fullMessage.getString());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Full message style: {}", fullMessage.getStyle());
		LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Full message contents type: {}", fullMessage.getContents().getClass().getSimpleName());
		
		if (!fullMessage.getSiblings().isEmpty()) {
			LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Full message has {} siblings", fullMessage.getSiblings().size());
			for (int idx = 0; idx < fullMessage.getSiblings().size(); idx++) {
				Component sibling = fullMessage.getSiblings().get(idx);
				LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Sibling[{}]: '{}', style: {}", idx, sibling.getString(), sibling.getStyle());
			}
		}
		
		// Check if the translatable component has the styled component in its args
		if (fullMessage.getContents() instanceof TranslatableContents translatableContents) {
			Object[] args = translatableContents.getArgs();
			LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: TranslatableContents has {} args", args.length);
			for (int idx = 0; idx < args.length; idx++) {
				Object arg = args[idx];
				if (arg instanceof Component argComponent) {
					LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Arg[{}] is Component: '{}', style: {}", 
						idx, argComponent.getString(), argComponent.getStyle());
					LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Arg[{}] color: {}, clickEvent: {}, hoverEvent: {}", 
						idx, argComponent.getStyle().getColor(), argComponent.getStyle().getClickEvent(), argComponent.getStyle().getHoverEvent());
				} else {
					LOGGER.info("[CHAT_STYLE_DEBUG] LocateCommand: Arg[{}] is {}: {}", idx, arg.getClass().getSimpleName(), arg);
				}
			}
		}
		
		commandSourceStack.sendSuccess(() -> fullMessage, false);
		
		LOGGER.info("Locating element {} took {} ms", string2, duration.toMillis());
		return i;
	}

	private static float dist(int i, int j, int k, int l) {
		int m = k - i;
		int n = l - j;
		return Mth.sqrt(m * m + n * n);
	}
}
