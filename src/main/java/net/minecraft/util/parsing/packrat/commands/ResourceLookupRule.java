package net.minecraft.util.parsing.packrat.commands;

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.NamedRule;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.Rule;
import org.jetbrains.annotations.Nullable;

public abstract class ResourceLookupRule<C, V> implements Rule<StringReader, V>, ResourceSuggestion {
	private final NamedRule<StringReader, ResourceLocation> idParser;
	protected final C context;
	private final DelayedException<CommandSyntaxException> error;

	protected ResourceLookupRule(NamedRule<StringReader, ResourceLocation> namedRule, C object) {
		this.idParser = namedRule;
		this.context = object;
		this.error = DelayedException.create(ResourceLocation.ERROR_INVALID);
	}

	@Nullable
	@Override
	public V parse(ParseState<StringReader> parseState) {
		parseState.input().skipWhitespace();
		int i = parseState.mark();
		ResourceLocation resourceLocation = parseState.parse(this.idParser);
		if (resourceLocation != null) {
			try {
				return this.validateElement(parseState.input(), resourceLocation);
			} catch (Exception var5) {
				parseState.errorCollector().store(i, this, var5);
				return null;
			}
		} else {
			parseState.errorCollector().store(i, this, this.error);
			return null;
		}
	}

	protected abstract V validateElement(ImmutableStringReader immutableStringReader, ResourceLocation resourceLocation) throws Exception;
}
