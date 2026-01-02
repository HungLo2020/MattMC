package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ComponentRenderUtils {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FormattedCharSequence INDENT = FormattedCharSequence.codepoint(32, Style.EMPTY);

	private static String stripColor(String string) {
		return Minecraft.getInstance().options.chatColors().get() ? string : ChatFormatting.stripFormatting(string);
	}

	public static List<FormattedCharSequence> wrapComponents(FormattedText formattedText, int i, Font font) {
		LOGGER.info("[CHAT_STYLE_DEBUG] ComponentRenderUtils.wrapComponents called for text: '{}'", formattedText.getString());
		ComponentCollector componentCollector = new ComponentCollector();
		formattedText.visit((style, string) -> {
			LOGGER.info("[CHAT_STYLE_DEBUG] ComponentRenderUtils.wrapComponents - visiting text: '{}', style: {}", string, style);
			LOGGER.info("[CHAT_STYLE_DEBUG] ComponentRenderUtils.wrapComponents - style color: {}, clickEvent: {}, hoverEvent: {}", 
				style.getColor(), style.getClickEvent(), style.getHoverEvent());
			componentCollector.append(FormattedText.of(stripColor(string), style));
			return Optional.empty();
		}, Style.EMPTY);
		List<FormattedCharSequence> list = Lists.<FormattedCharSequence>newArrayList();
		font.getSplitter().splitLines(componentCollector.getResultOrEmpty(), i, Style.EMPTY, (BiConsumer<FormattedText, Boolean>)((formattedTextx, boolean_) -> {
			FormattedCharSequence formattedCharSequence = Language.getInstance().getVisualOrder(formattedTextx);
			LOGGER.info("[CHAT_STYLE_DEBUG] ComponentRenderUtils.wrapComponents - created FormattedCharSequence: '{}'", formattedCharSequence);
			list.add(boolean_ ? FormattedCharSequence.composite(INDENT, formattedCharSequence) : formattedCharSequence);
		}));
		LOGGER.info("[CHAT_STYLE_DEBUG] ComponentRenderUtils.wrapComponents - created {} lines", list.size());
		return (List<FormattedCharSequence>)(list.isEmpty() ? Lists.<FormattedCharSequence>newArrayList(FormattedCharSequence.EMPTY) : list);
	}
}
