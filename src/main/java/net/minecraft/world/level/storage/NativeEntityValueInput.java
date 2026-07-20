package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import org.jetbrains.annotations.Nullable;

/**
 * Prototype entity-loading {@link ValueInput} backed by MattMC's native NBT
 * tape instead of a Java {@link CompoundTag}.
 *
 * <p>This is intentionally dev/test-only plumbing. Construction indexes tape
 * record offsets and subtree ranges, then field reads go back to the tape
 * lazily. Java {@link Tag} objects are materialized only for codec-requested
 * subtrees.
 */
public final class NativeEntityValueInput implements ValueInput {
	private static final int MAGIC = 0x5442544E;
	private static final int VERSION = 1;
	private static final int RECORD_LEN = 24;
	private static final int NO_CHILDREN = -1;
	private final ProblemReporter problemReporter;
	private final ValueInputContextHelper context;
	private final TapeIndex tape;
	private final int record;

	private NativeEntityValueInput(ProblemReporter problemReporter, ValueInputContextHelper context, TapeIndex tape, int record) {
		this.problemReporter = problemReporter;
		this.context = context;
		this.tape = tape;
		this.record = record;
	}

	public static NativeEntityValueInput create(ProblemReporter problemReporter, HolderLookup.Provider provider, byte[] tape) throws IOException {
		return createForTesting(problemReporter, provider, indexTapeForTesting(tape));
	}

	static TapeIndex indexTapeForTesting(byte[] tape) throws IOException {
		return TapeIndex.parse(tape);
	}

	static NativeEntityValueInput createForTesting(ProblemReporter problemReporter, HolderLookup.Provider provider, TapeIndex tape) throws IOException {
		if (tape.type(0) != TagType.COMPOUND) {
			throw new IOException("Native entity ValueInput root must be a compound");
		}
		return new NativeEntityValueInput(problemReporter, new ValueInputContextHelper(provider, NbtOps.INSTANCE), tape, 0);
	}

	public static ValueInput.ValueInputList createList(ProblemReporter problemReporter, HolderLookup.Provider provider, List<byte[]> tapes) throws IOException {
		return createListFromSlices(
			problemReporter,
			provider,
			tapes.stream().map(tape -> new TapeSlice(tape, 0, tape.length)).toList()
		);
	}

	public static ValueInput.ValueInputList createListFromSlices(ProblemReporter problemReporter, HolderLookup.Provider provider, List<TapeSlice> tapes) throws IOException {
		ValueInputContextHelper context = new ValueInputContextHelper(provider, NbtOps.INSTANCE);
		NativeEntityValueInput[] inputs = new NativeEntityValueInput[tapes.size()];
		for (int i = 0; i < tapes.size(); i++) {
			TapeSlice slice = tapes.get(i);
			TapeIndex tape = TapeIndex.parse(slice.bytes, slice.offset, slice.length);
			if (tape.type(0) != TagType.COMPOUND) {
				throw new IOException("Native entity ValueInput list element must be a compound");
			}
			inputs[i] = new NativeEntityValueInput(problemReporter.forChild(new ProblemReporter.IndexedPathElement(i)), context, tape, 0);
		}
		return new PrebuiltListWrapper(inputs);
	}

	static ValueInput.ValueInputList createListForTesting(ProblemReporter problemReporter, HolderLookup.Provider provider, List<byte[]> tapes) throws IOException {
		return createList(problemReporter, provider, tapes);
	}

	public record TapeSlice(byte[] bytes, int offset, int length) {
		public TapeSlice {
			if (offset < 0 || length < 0 || bytes.length - offset < length) {
				throw new IndexOutOfBoundsException("Invalid native entity tape slice");
			}
		}
	}

	@Override
	public <T> Optional<T> read(String name, Codec<T> codec) {
		int child = this.find(name);
		if (child < 0) {
			return Optional.empty();
		}
		Tag tag = this.tape.toTag(child);
		return switch (codec.parse(this.context.ops(), tag)) {
			case Success<T> success -> Optional.of(success.value());
			case Error<T> error -> {
				this.problemReporter.report(new TagValueInput.DecodeFromFieldFailedProblem(name, tag, error));
				yield error.partialValue();
			}
			default -> throw new MatchException(null, null);
		};
	}

	@Override
	public <T> Optional<T> read(MapCodec<T> mapCodec) {
		DynamicOps<Tag> ops = this.context.ops();
		CompoundTag tag = (CompoundTag)this.tape.toTag(this.record);
		return switch (ops.getMap(tag).flatMap(mapLike -> mapCodec.decode(ops, mapLike))) {
			case Success<T> success -> Optional.of(success.value());
			case Error<T> error -> {
				this.problemReporter.report(new TagValueInput.DecodeFromMapFailedProblem(error));
				yield error.partialValue();
			}
			default -> throw new MatchException(null, null);
		};
	}

	@Override
	public Optional<ValueInput> child(String name) {
		int child = this.find(name);
		if (child < 0) {
			return Optional.empty();
		}
		if (this.tape.type(child) != TagType.COMPOUND) {
			this.problemReporter.report(new UnexpectedTapeTypeProblem(name, "compound", this.tape.type(child).name));
			return Optional.empty();
		}
		return Optional.of(this.wrapChild(name, child));
	}

	@Override
	public ValueInput childOrEmpty(String name) {
		return this.child(name).orElse(this.context.empty());
	}

	@Override
	public Optional<ValueInput.ValueInputList> childrenList(String name) {
		int child = this.find(name);
		if (child < 0) {
			return Optional.empty();
		}
		if (this.tape.type(child) != TagType.LIST) {
			this.problemReporter.report(new UnexpectedTapeTypeProblem(name, "list", this.tape.type(child).name));
			return Optional.empty();
		}
		return Optional.of(this.wrapList(name, child));
	}

	@Override
	public ValueInput.ValueInputList childrenListOrEmpty(String name) {
		return this.childrenList(name).orElse(this.context.emptyList());
	}

	@Override
	public <T> Optional<ValueInput.TypedInputList<T>> list(String name, Codec<T> codec) {
		int child = this.find(name);
		if (child < 0) {
			return Optional.empty();
		}
		if (this.tape.type(child) != TagType.LIST) {
			this.problemReporter.report(new UnexpectedTapeTypeProblem(name, "list", this.tape.type(child).name));
			return Optional.empty();
		}
		return Optional.of(new TypedListWrapper<>(this.problemReporter, name, this.context, codec, this.tape, child));
	}

	@Override
	public <T> ValueInput.TypedInputList<T> listOrEmpty(String name, Codec<T> codec) {
		return this.list(name, codec).orElse(this.context.emptyTypedList());
	}

	@Override
	public boolean getBooleanOr(String name, boolean fallback) {
		int child = this.numeric(name);
		return child >= 0 ? this.tape.byteValue(child) != 0 : fallback;
	}

	@Override
	public byte getByteOr(String name, byte fallback) {
		int child = this.numeric(name);
		return child >= 0 ? this.tape.byteValue(child) : fallback;
	}

	@Override
	public int getShortOr(String name, short fallback) {
		int child = this.numeric(name);
		return child >= 0 ? this.tape.shortValue(child) : fallback;
	}

	@Override
	public Optional<Integer> getInt(String name) {
		int child = this.numeric(name);
		return child >= 0 ? Optional.of(this.tape.intValue(child)) : Optional.empty();
	}

	@Override
	public int getIntOr(String name, int fallback) {
		int child = this.numeric(name);
		return child >= 0 ? this.tape.intValue(child) : fallback;
	}

	@Override
	public long getLongOr(String name, long fallback) {
		int child = this.numeric(name);
		return child >= 0 ? this.tape.longValue(child) : fallback;
	}

	@Override
	public Optional<Long> getLong(String name) {
		int child = this.numeric(name);
		return child >= 0 ? Optional.of(this.tape.longValue(child)) : Optional.empty();
	}

	@Override
	public float getFloatOr(String name, float fallback) {
		int child = this.numeric(name);
		return child >= 0 ? this.tape.floatValue(child) : fallback;
	}

	@Override
	public double getDoubleOr(String name, double fallback) {
		int child = this.numeric(name);
		return child >= 0 ? this.tape.doubleValue(child) : fallback;
	}

	@Override
	public Optional<String> getString(String name) {
		int child = this.find(name);
		if (child < 0) {
			return Optional.empty();
		}
		if (this.tape.type(child) != TagType.STRING) {
			this.problemReporter.report(new UnexpectedTapeTypeProblem(name, "string", this.tape.type(child).name));
			return Optional.empty();
		}
		return Optional.of(this.tape.stringValue(child));
	}

	@Override
	public String getStringOr(String name, String fallback) {
		return this.getString(name).orElse(fallback);
	}

	@Override
	public Optional<int[]> getIntArray(String name) {
		int child = this.find(name);
		if (child < 0) {
			return Optional.empty();
		}
		if (this.tape.type(child) != TagType.INT_ARRAY) {
			this.problemReporter.report(new UnexpectedTapeTypeProblem(name, "int array", this.tape.type(child).name));
			return Optional.empty();
		}
		return Optional.of(this.tape.intArrayValue(child));
	}

	@Override
	public HolderLookup.Provider lookup() {
		return this.context.lookup();
	}

	private int numeric(String name) {
		int child = this.find(name);
		if (child < 0) {
			return -1;
		}
		if (!this.tape.type(child).numeric) {
			this.problemReporter.report(new UnexpectedTapeTypeProblem(name, "number", this.tape.type(child).name));
			return -1;
		}
		return child;
	}

	private int find(String name) {
		if (this.tape.type(this.record) != TagType.COMPOUND) {
			return -1;
		}
		return this.tape.findChild(this.record, name);
	}

	private ValueInput wrapChild(String name, int child) {
		return this.tape.childCount(child) == 0
			? this.context.empty()
			: new NativeEntityValueInput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name)), this.context, this.tape, child);
	}

	private ValueInput.ValueInputList wrapList(String name, int child) {
		return this.tape.childCount(child) == 0 ? this.context.emptyList() : new ListWrapper(this.problemReporter, name, this.context, this.tape, child);
	}

	public record UnexpectedTapeTypeProblem(String name, String expected, String actual) implements ProblemReporter.Problem {
		@Override
		public String description() {
			return "Field '" + this.name + "' expected " + this.expected + " but native tape contained " + this.actual;
		}
	}

	static final class PrebuiltListWrapper implements ValueInput.ValueInputList {
		private final NativeEntityValueInput[] inputs;

		PrebuiltListWrapper(NativeEntityValueInput[] inputs) {
			this.inputs = inputs;
		}

		@Override
		public boolean isEmpty() {
			return this.inputs.length == 0;
		}

		@Override
		public Stream<ValueInput> stream() {
			return Arrays.stream(this.inputs).map(input -> input);
		}

		@Override
		public Iterator<ValueInput> iterator() {
			return new Iterator<>() {
				private int index;

				@Override
				public boolean hasNext() {
					return this.index < PrebuiltListWrapper.this.inputs.length;
				}

				@Override
				public ValueInput next() {
					if (!this.hasNext()) {
						throw new NoSuchElementException();
					}
					return PrebuiltListWrapper.this.inputs[this.index++];
				}
			};
		}
	}

	static final class ListWrapper implements ValueInput.ValueInputList {
		private final ProblemReporter problemReporter;
		private final String name;
		private final ValueInputContextHelper context;
		private final TapeIndex tape;
		private final int listRecord;

		ListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, TapeIndex tape, int listRecord) {
			this.problemReporter = problemReporter;
			this.name = name;
			this.context = context;
			this.tape = tape;
			this.listRecord = listRecord;
		}

		@Override
		public boolean isEmpty() {
			return this.tape.childCount(this.listRecord) == 0;
		}

		@Override
		public Stream<ValueInput> stream() {
			return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this.iterator(), 0), false);
		}

		@Override
		public Iterator<ValueInput> iterator() {
			return new Iterator<>() {
				private int remaining = ListWrapper.this.tape.childCount(ListWrapper.this.listRecord);
				private int current = ListWrapper.this.tape.firstChild(ListWrapper.this.listRecord);

				@Override
				public boolean hasNext() {
					return this.remaining > 0;
				}

				@Override
				public ValueInput next() {
					if (!this.hasNext()) {
						throw new NoSuchElementException();
					}
					int child = this.current;
					this.current = ListWrapper.this.tape.nextSibling(child);
					this.remaining--;
					if (ListWrapper.this.tape.type(child) != TagType.COMPOUND || ListWrapper.this.tape.childCount(child) == 0) {
						return ListWrapper.this.context.empty();
					}
					return new NativeEntityValueInput(
						ListWrapper.this.problemReporter.forChild(new ProblemReporter.FieldPathElement(ListWrapper.this.name)),
						ListWrapper.this.context,
						ListWrapper.this.tape,
						child
					);
				}
			};
		}
	}

	static final class TypedListWrapper<T> implements ValueInput.TypedInputList<T> {
		private final ProblemReporter problemReporter;
		private final String name;
		private final ValueInputContextHelper context;
		private final Codec<T> codec;
		private final TapeIndex tape;
		private final int listRecord;

		TypedListWrapper(ProblemReporter problemReporter, String name, ValueInputContextHelper context, Codec<T> codec, TapeIndex tape, int listRecord) {
			this.problemReporter = problemReporter;
			this.name = name;
			this.context = context;
			this.codec = codec;
			this.tape = tape;
			this.listRecord = listRecord;
		}

		@Override
		public boolean isEmpty() {
			return this.tape.childCount(this.listRecord) == 0;
		}

		@Override
		public Stream<T> stream() {
			return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this.iterator(), 0), false);
		}

		@Override
		public Iterator<T> iterator() {
			return new Iterator<>() {
				private int remaining = TypedListWrapper.this.tape.childCount(TypedListWrapper.this.listRecord);
				private int current = TypedListWrapper.this.tape.firstChild(TypedListWrapper.this.listRecord);
				private T next;
				private boolean ready;

				@Override
				public boolean hasNext() {
					if (this.ready) {
						return true;
					}
					while (this.remaining > 0) {
						int child = this.current;
						this.current = TypedListWrapper.this.tape.nextSibling(child);
						this.remaining--;
						Tag tag = TypedListWrapper.this.tape.toTag(child);
						Optional<T> decoded = switch (TypedListWrapper.this.codec.parse(TypedListWrapper.this.context.ops(), tag)) {
							case Success<T> success -> Optional.of(success.value());
							case Error<T> error -> {
								TypedListWrapper.this.problemReporter.report(new TagValueInput.DecodeFromFieldFailedProblem(TypedListWrapper.this.name, tag, error));
								yield error.partialValue();
							}
							default -> throw new MatchException(null, null);
						};
						if (decoded.isPresent()) {
							this.next = decoded.orElseThrow();
							this.ready = true;
							return true;
						}
					}
					return false;
				}

				@Override
				public T next() {
					if (!this.hasNext()) {
						throw new NoSuchElementException();
					}
					T value = this.next;
					this.next = null;
					this.ready = false;
					return value;
				}
			};
		}
	}

	enum TagType {
		END(0, "end", false),
		BYTE(1, "byte", true),
		SHORT(2, "short", true),
		INT(3, "int", true),
		LONG(4, "long", true),
		FLOAT(5, "float", true),
		DOUBLE(6, "double", true),
		BYTE_ARRAY(7, "byte array", false),
		STRING(8, "string", false),
		LIST(9, "list", false),
		COMPOUND(10, "compound", false),
		INT_ARRAY(11, "int array", false),
		LONG_ARRAY(12, "long array", false);

		final int id;
		final String name;
		final boolean numeric;

		TagType(int id, String name, boolean numeric) {
			this.id = id;
			this.name = name;
			this.numeric = numeric;
		}

		static TagType byId(int id, int offset) throws IOException {
			for (TagType type : values()) {
				if (type.id == id) {
					return type;
				}
			}
			throw new IOException("Invalid native NBT tape tag id " + id + " at " + offset);
		}
	}

	static final class TapeIndex {
		private final byte[] bytes;
		private int cursor;
		private final int end;
		private byte[] types = new byte[32];
		private byte[] listTypes = new byte[32];
		private int[] recordOffsets = new int[32];
		private int[] nameOffsets = new int[32];
		private int[] payloadOffsets = new int[32];
		private int[] childStarts = new int[32];
		private int[] childCounts = new int[32];
		private int[] subtreeEnds = new int[32];
		private int size;

		private TapeIndex(byte[] bytes, int offset, int length) {
			this.bytes = bytes;
			this.cursor = offset;
			this.end = offset + length;
			Arrays.fill(this.childStarts, NO_CHILDREN);
		}

		static TapeIndex parse(byte[] bytes) throws IOException {
			return parse(bytes, 0, bytes.length);
		}

		static TapeIndex parse(byte[] bytes, int offset, int length) throws IOException {
			TapeIndex index = new TapeIndex(bytes, offset, length);
			if (index.readIntLE() != MAGIC) {
				throw new IOException("Invalid native NBT tape magic");
			}
			if (index.readUnsignedShortLE() != VERSION) {
				throw new IOException("Unsupported native NBT tape version");
			}
			index.readUnsignedShortLE();
			index.readRecord();
			if (index.cursor != index.end) {
				throw new IOException("Trailing bytes in native NBT tape at " + index.cursor);
			}
			return index;
		}

		TagType type(int record) {
			try {
				return TagType.byId(this.types[record] & 0xFF, this.recordOffsets[record]);
			} catch (IOException exception) {
				throw new IllegalStateException(exception);
			}
		}

		int childCount(int record) {
			return this.childCounts[record];
		}

		int firstChild(int record) {
			return this.childStarts[record];
		}

		int nextSibling(int record) {
			return this.subtreeEnds[record];
		}

		int findChild(int parent, String name) {
			int found = -1;
			int remaining = this.childCounts[parent];
			int child = this.childStarts[parent];
			while (remaining-- > 0) {
				if (this.nameEquals(child, name)) {
					found = child;
				}
				child = this.subtreeEnds[child];
			}
			return found;
		}

		byte byteValue(int record) {
			return (byte)this.scalar(record);
		}

		short shortValue(int record) {
			return switch (this.type(record)) {
				case BYTE -> this.byteValue(record);
				case SHORT -> (short)this.scalar(record);
				default -> (short)this.longValue(record);
			};
		}

		int intValue(int record) {
			return switch (this.type(record)) {
				case BYTE -> this.byteValue(record);
				case SHORT -> this.shortValue(record);
				case INT -> (int)this.scalar(record);
				default -> (int)this.longValue(record);
			};
		}

		long longValue(int record) {
			return switch (this.type(record)) {
				case BYTE -> this.byteValue(record);
				case SHORT -> this.shortValue(record);
				case INT -> (int)this.scalar(record);
				case LONG -> this.scalar(record);
				case FLOAT -> (long)this.floatValue(record);
				case DOUBLE -> (long)this.doubleValue(record);
				default -> this.scalar(record);
			};
		}

		float floatValue(int record) {
			return switch (this.type(record)) {
				case FLOAT -> Float.intBitsToFloat((int)this.scalar(record));
				case DOUBLE -> (float)Double.longBitsToDouble(this.scalar(record));
				default -> this.longValue(record);
			};
		}

		double doubleValue(int record) {
			return switch (this.type(record)) {
				case FLOAT -> Float.intBitsToFloat((int)this.scalar(record));
				case DOUBLE -> Double.longBitsToDouble(this.scalar(record));
				default -> this.longValue(record);
			};
		}

		String stringValue(int record) {
			return this.readUtf16String(this.payloadOffsets[record], this.itemCount(record));
		}

		int[] intArrayValue(int record) {
			int count = this.itemCount(record);
			int offset = this.payloadOffsets[record];
			int[] values = new int[count];
			for (int i = 0; i < count; i++) {
				values[i] = readIntLE(this.bytes, offset + i * 4);
			}
			return values;
		}

		Tag toTag(int record) {
			return switch (this.type(record)) {
				case BYTE -> ByteTag.valueOf(this.byteValue(record));
				case SHORT -> ShortTag.valueOf((short)this.scalar(record));
				case INT -> IntTag.valueOf((int)this.scalar(record));
				case LONG -> LongTag.valueOf(this.scalar(record));
				case FLOAT -> FloatTag.valueOf(Float.intBitsToFloat((int)this.scalar(record)));
				case DOUBLE -> DoubleTag.valueOf(Double.longBitsToDouble(this.scalar(record)));
				case BYTE_ARRAY -> new ByteArrayTag(this.byteArrayValue(record));
				case STRING -> StringTag.valueOf(this.stringValue(record));
				case LIST -> {
					ListTag listTag = new ListTag();
					int remaining = this.childCounts[record];
					int child = this.childStarts[record];
					while (remaining-- > 0) {
						listTag.add(this.toTag(child));
						child = this.subtreeEnds[child];
					}
					yield listTag;
				}
				case COMPOUND -> {
					CompoundTag compoundTag = new CompoundTag();
					int remaining = this.childCounts[record];
					int child = this.childStarts[record];
					while (remaining-- > 0) {
						compoundTag.put(this.name(child), this.toTag(child));
						child = this.subtreeEnds[child];
					}
					yield compoundTag;
				}
				case INT_ARRAY -> new IntArrayTag(this.intArrayValue(record));
				case LONG_ARRAY -> new LongArrayTag(this.longArrayValue(record));
				case END -> throw new IllegalStateException("End tag cannot be materialized");
			};
		}

		private int readRecord() throws IOException {
			require(this.cursor, RECORD_LEN);
			int recordOffset = this.cursor;
			TagType type = TagType.byId(this.bytes[recordOffset] & 0xFF, recordOffset);
			TagType listElementType = TagType.byId(this.bytes[recordOffset + 1] & 0xFF, recordOffset + 1);
			if (type == TagType.END) {
				throw new IOException("Unexpected end tag in native NBT tape at " + recordOffset);
			}
			int nameUnits = readCount(recordOffset + 4, "name");
			int count = readCount(recordOffset + 8, "item");
			this.cursor += RECORD_LEN;
			int nameOffset = this.cursor;
			this.skipUtf16(nameUnits);
			int payloadOffset = this.cursor;
			int record = this.addRecord(type, listElementType, recordOffset, nameOffset, payloadOffset);

			switch (type) {
				case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> {}
				case BYTE_ARRAY -> this.skipBytes(count, 1);
				case STRING -> this.skipUtf16(count);
				case INT_ARRAY -> this.skipBytes(count, 4);
				case LONG_ARRAY -> this.skipBytes(count, 8);
				case LIST, COMPOUND -> {
					if (type == TagType.LIST && listElementType == TagType.END && count > 0) {
						throw new IOException("Non-empty native NBT tape list has no element type at " + recordOffset);
					}
					this.childStarts[record] = count == 0 ? NO_CHILDREN : this.size;
					this.childCounts[record] = count;
					for (int i = 0; i < count; i++) {
						int child = this.readRecord();
						if (type == TagType.LIST && this.type(child) != listElementType) {
							throw new IOException("Native NBT tape list element type mismatch at " + this.recordOffsets[child]);
						}
					}
				}
				case END -> throw new IOException("Unexpected end tag in native NBT tape at " + recordOffset);
			}
			this.subtreeEnds[record] = this.size;
			return record;
		}

		private int addRecord(TagType type, TagType listElementType, int recordOffset, int nameOffset, int payloadOffset) {
			this.ensureCapacity(this.size + 1);
			int record = this.size++;
			this.types[record] = (byte)type.id;
			this.listTypes[record] = (byte)listElementType.id;
			this.recordOffsets[record] = recordOffset;
			this.nameOffsets[record] = nameOffset;
			this.payloadOffsets[record] = payloadOffset;
			this.childStarts[record] = NO_CHILDREN;
			this.childCounts[record] = 0;
			this.subtreeEnds[record] = record + 1;
			return record;
		}

		private void ensureCapacity(int needed) {
			if (needed <= this.recordOffsets.length) {
				return;
			}
			int capacity = this.recordOffsets.length * 2;
			while (capacity < needed) {
				capacity *= 2;
			}
			this.types = Arrays.copyOf(this.types, capacity);
			this.listTypes = Arrays.copyOf(this.listTypes, capacity);
			this.recordOffsets = Arrays.copyOf(this.recordOffsets, capacity);
			this.nameOffsets = Arrays.copyOf(this.nameOffsets, capacity);
			this.payloadOffsets = Arrays.copyOf(this.payloadOffsets, capacity);
			int oldLength = this.childStarts.length;
			this.childStarts = Arrays.copyOf(this.childStarts, capacity);
			Arrays.fill(this.childStarts, oldLength, capacity, NO_CHILDREN);
			this.childCounts = Arrays.copyOf(this.childCounts, capacity);
			this.subtreeEnds = Arrays.copyOf(this.subtreeEnds, capacity);
		}

		private boolean nameEquals(int record, String name) {
			int units = this.nameUnits(record);
			if (name.length() != units) {
				return false;
			}
			int offset = this.nameOffsets[record];
			for (int i = 0; i < units; i++) {
				if (name.charAt(i) != (char)readUnsignedShortLE(this.bytes, offset + i * 2)) {
					return false;
				}
			}
			return true;
		}

		private String name(int record) {
			return this.readUtf16String(this.nameOffsets[record], this.nameUnits(record));
		}

		private int nameUnits(int record) {
			return readIntLE(this.bytes, this.recordOffsets[record] + 4);
		}

		private int itemCount(int record) {
			return readIntLE(this.bytes, this.recordOffsets[record] + 8);
		}

		private long scalar(int record) {
			return readLongLE(this.bytes, this.recordOffsets[record] + 16);
		}

		private byte[] byteArrayValue(int record) {
			int count = this.itemCount(record);
			return Arrays.copyOfRange(this.bytes, this.payloadOffsets[record], this.payloadOffsets[record] + count);
		}

		private long[] longArrayValue(int record) {
			int count = this.itemCount(record);
			int offset = this.payloadOffsets[record];
			long[] values = new long[count];
			for (int i = 0; i < count; i++) {
				values[i] = readLongLE(this.bytes, offset + i * 8);
			}
			return values;
		}

		private String readUtf16String(int offset, int units) {
			char[] chars = new char[units];
			for (int i = 0; i < units; i++) {
				chars[i] = (char)readUnsignedShortLE(this.bytes, offset + i * 2);
			}
			return new String(chars);
		}

		private int readIntLE() throws IOException {
			require(this.cursor, 4);
			int value = readIntLE(this.bytes, this.cursor);
			this.cursor += 4;
			return value;
		}

		private int readUnsignedShortLE() throws IOException {
			require(this.cursor, 2);
			int value = readUnsignedShortLE(this.bytes, this.cursor);
			this.cursor += 2;
			return value;
		}

		private int readCount(int offset, String field) throws IOException {
			int value = readIntLE(this.bytes, offset);
			if (value < 0) {
				throw new IOException("Oversized native NBT tape " + field + " count at " + offset);
			}
			return value;
		}

		private void skipUtf16(int units) throws IOException {
			this.skipBytes(units, 2);
		}

		private void skipBytes(int count, int elementSize) throws IOException {
			if (count < 0 || count > Integer.MAX_VALUE / elementSize) {
				throw new IOException("Oversized native NBT tape payload at " + this.cursor);
			}
			int length = count * elementSize;
			require(this.cursor, length);
			this.cursor += length;
		}

		private void require(int offset, int length) throws IOException {
			if (length < 0 || offset < 0 || this.end - offset < length) {
				throw new IOException("Truncated native NBT tape at " + offset);
			}
		}

		private static int readIntLE(byte[] bytes, int offset) {
			return bytes[offset] & 0xFF
				| (bytes[offset + 1] & 0xFF) << 8
				| (bytes[offset + 2] & 0xFF) << 16
				| bytes[offset + 3] << 24;
		}

		private static int readUnsignedShortLE(byte[] bytes, int offset) {
			return bytes[offset] & 0xFF | (bytes[offset + 1] & 0xFF) << 8;
		}

		private static long readLongLE(byte[] bytes, int offset) {
			return bytes[offset] & 0xFFL
				| (bytes[offset + 1] & 0xFFL) << 8
				| (bytes[offset + 2] & 0xFFL) << 16
				| (bytes[offset + 3] & 0xFFL) << 24
				| (bytes[offset + 4] & 0xFFL) << 32
				| (bytes[offset + 5] & 0xFFL) << 40
				| (bytes[offset + 6] & 0xFFL) << 48
				| (long)bytes[offset + 7] << 56;
		}
	}
}
