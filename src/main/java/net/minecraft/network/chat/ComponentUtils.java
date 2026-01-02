package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class ComponentUtils {
	public static final String DEFAULT_SEPARATOR_TEXT = ", ";
	public static final Component DEFAULT_SEPARATOR = Component.literal(", ").withStyle(ChatFormatting.GRAY);
	public static final Component DEFAULT_NO_STYLE_SEPARATOR = Component.literal(", ");
	private static final int MAX_RECURSION_DEPTH = 100;

	public static MutableComponent mergeStyles(MutableComponent mutableComponent, Style style) {
		if (style.isEmpty()) {
			return mutableComponent;
		} else {
			Style style2 = mutableComponent.getStyle();
			if (style2.isEmpty()) {
				return mutableComponent.setStyle(style);
			} else {
				return style2.equals(style) ? mutableComponent : mutableComponent.setStyle(style2.applyTo(style));
			}
		}
	}

	public static Optional<MutableComponent> updateForEntity(
		@Nullable CommandSourceStack commandSourceStack, Optional<Component> optional, @Nullable Entity entity, int i
	) throws CommandSyntaxException {
		return optional.isPresent() ? Optional.of(updateForEntity(commandSourceStack, (Component)optional.get(), entity, i)) : Optional.empty();
	}

	public static MutableComponent updateForEntity(@Nullable CommandSourceStack commandSourceStack, Component component, @Nullable Entity entity, int i) throws CommandSyntaxException {
		if (i > MAX_RECURSION_DEPTH) {
			return component.copy();
		} else {
			MutableComponent mutableComponent = component.getContents().resolve(commandSourceStack, entity, i + 1);

			for (Component component2 : component.getSiblings()) {
				mutableComponent.append(updateForEntity(commandSourceStack, component2, entity, i + 1));
			}

			return mutableComponent.withStyle(resolveStyle(commandSourceStack, component.getStyle(), entity, i));
		}
	}

	private static Style resolveStyle(@Nullable CommandSourceStack commandSourceStack, Style style, @Nullable Entity entity, int i) throws CommandSyntaxException {
		if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component hoverEvent2)) {
			HoverEvent hoverEvent2x = new HoverEvent.ShowText(updateForEntity(commandSourceStack, hoverEvent2, entity, i + 1));
			return style.withHoverEvent(hoverEvent2x);
		} else {
			return style;
		}
	}

	public static Component formatList(Collection<String> collection) {
		return formatAndSortList(collection, string -> Component.literal(string).withStyle(ChatFormatting.GREEN));
	}

	@SuppressWarnings("unchecked")
	public static <T extends Comparable<T>> Component formatAndSortList(Collection<T> collection, Function<T, Component> function) {
		if (collection.isEmpty()) {
			return CommonComponents.EMPTY;
		} else if (collection.size() == 1) {
			return (Component)function.apply((T)collection.iterator().next());
		} else {
			List<T> list = Lists.<T>newArrayList(collection);
			list.sort(Comparable::compareTo);
			return formatList(list, function);
		}
	}

	public static <T> Component formatList(Collection<? extends T> collection, Function<T, Component> function) {
		return formatList(collection, DEFAULT_SEPARATOR, function);
	}

	public static <T> MutableComponent formatList(Collection<? extends T> collection, Optional<? extends Component> optional, Function<T, Component> function) {
		return formatList(collection, DataFixUtils.orElse(optional, DEFAULT_SEPARATOR), function);
	}

	public static Component formatList(Collection<? extends Component> collection, Component component) {
		return formatList(collection, component, Function.identity());
	}

	public static <T> MutableComponent formatList(Collection<? extends T> collection, Component component, Function<T, Component> function) {
		if (collection.isEmpty()) {
			return Component.empty();
		} else if (collection.size() == 1) {
			return ((Component)function.apply(collection.iterator().next())).copy();
		} else {
			MutableComponent mutableComponent = Component.empty();
			boolean bl = true;

			for (T object : collection) {
				if (!bl) {
					mutableComponent.append(component);
				}

				mutableComponent.append((Component)function.apply(object));
				bl = false;
			}

			return mutableComponent;
		}
	}

	public static MutableComponent wrapInSquareBrackets(Component component) {
		return Component.translatable("chat.square_brackets", component);
	}

	public static Component fromMessage(Message message) {
		return (Component)(message instanceof Component component ? component : Component.literal(message.getString()));
	}

	public static boolean isTranslationResolvable(@Nullable Component component) {
		if (component != null && component.getContents() instanceof TranslatableContents translatableContents) {
			String string = translatableContents.getKey();
			String string2 = translatableContents.getFallback();
			return string2 != null || Language.getInstance().has(string);
		} else {
			return true;
		}
	}

	public static MutableComponent copyOnClickText(String string) {
		return wrapInSquareBrackets(
			Component.literal(string)
				.withStyle(
					style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.CopyToClipboard(string))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click")))
						.withInsertion(string)
				)
		);
	}
}
