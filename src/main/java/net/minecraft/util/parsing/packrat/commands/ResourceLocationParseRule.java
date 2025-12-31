package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jetbrains.annotations.Nullable;

public class ResourceLocationParseRule implements Rule<StringReader, ResourceLocation> {
	public static final Rule<StringReader, ResourceLocation> INSTANCE = new ResourceLocationParseRule();

	private ResourceLocationParseRule() {
	}

	@Nullable
	public ResourceLocation parse(ParseState<StringReader> parseState) {
		parseState.input().skipWhitespace();

		try {
			return ResourceLocation.readNonEmpty(parseState.input());
		} catch (CommandSyntaxException var3) {
			return null;
		}
	}
}
