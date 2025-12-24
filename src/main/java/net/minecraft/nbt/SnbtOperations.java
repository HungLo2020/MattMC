package net.minecraft.nbt;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.parsing.packrat.DelayedException;
import net.minecraft.util.parsing.packrat.ParseState;
import net.minecraft.util.parsing.packrat.SuggestionSupplier;
import org.jetbrains.annotations.Nullable;

public class SnbtOperations {
	static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_STRING_UUID = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_string_uuid"))
	);
	static final DelayedException<CommandSyntaxException> ERROR_EXPECTED_NUMBER_OR_BOOLEAN = DelayedException.create(
		new SimpleCommandExceptionType(Component.translatable("snbt.parser.expected_number_or_boolean"))
	);
	public static final String BUILTIN_TRUE = "true";
	public static final String BUILTIN_FALSE = "false";
	public static final Map<SnbtOperations.BuiltinKey, SnbtOperations.BuiltinOperation> BUILTIN_OPERATIONS = Map.of(
		new SnbtOperations.BuiltinKey("bool", 1), new SnbtOperations.BuiltinOperation() {
			@Override
			public <T> T run(DynamicOps<T> dynamicOps, List<T> list, ParseState<StringReader> parseState) {
				Boolean boolean_ = convert(dynamicOps, (T)list.getFirst());
				if (boolean_ == null) {
					parseState.errorCollector().store(parseState.mark(), SnbtOperations.ERROR_EXPECTED_NUMBER_OR_BOOLEAN);
					return null;
				} else {
					return dynamicOps.createBoolean(boolean_);
				}
			}

			@Nullable
			private static <T> Boolean convert(DynamicOps<T> dynamicOps, T object) {
				Optional<Boolean> optional = dynamicOps.getBooleanValue(object).result();
				if (optional.isPresent()) {
					return (Boolean)optional.get();
				} else {
					Optional<Number> optional2 = dynamicOps.getNumberValue(object).result();
					return optional2.isPresent() ? ((Number)optional2.get()).doubleValue() != 0.0 : null;
				}
			}
		}, new SnbtOperations.BuiltinKey("uuid", 1), new SnbtOperations.BuiltinOperation() {
			@Override
			public <T> T run(DynamicOps<T> dynamicOps, List<T> list, ParseState<StringReader> parseState) {
				Optional<String> optional = dynamicOps.getStringValue((T)list.getFirst()).result();
				if (optional.isEmpty()) {
					parseState.errorCollector().store(parseState.mark(), SnbtOperations.ERROR_EXPECTED_STRING_UUID);
					return null;
				} else {
					UUID uUID;
					try {
						uUID = UUID.fromString((String)optional.get());
					} catch (IllegalArgumentException var7) {
						parseState.errorCollector().store(parseState.mark(), SnbtOperations.ERROR_EXPECTED_STRING_UUID);
						return null;
					}

					return dynamicOps.createIntList(IntStream.of(UUIDUtil.uuidToIntArray(uUID)));
				}
			}
		}
	);
	public static final SuggestionSupplier<StringReader> BUILTIN_IDS = new SuggestionSupplier<StringReader>() {
		private final Set<String> keys = (Set<String>)Stream.concat(
				Stream.of("false", "true"), SnbtOperations.BUILTIN_OPERATIONS.keySet().stream().map(SnbtOperations.BuiltinKey::id)
			)
			.collect(Collectors.toSet());

		@Override
		public Stream<String> possibleValues(ParseState<StringReader> parseState) {
			return this.keys.stream();
		}
	};

	public record BuiltinKey(String id, int argCount) {
		public String toString() {
			return this.id + "/" + this.argCount;
		}
	}

	public interface BuiltinOperation {
		@Nullable
		<T> T run(DynamicOps<T> dynamicOps, List<T> list, ParseState<StringReader> parseState);
	}
}
