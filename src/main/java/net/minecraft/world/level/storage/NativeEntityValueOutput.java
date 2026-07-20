package net.minecraft.world.level.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.DataResult.Success;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Dev/test-only {@link ValueOutput} for entity-save experiments.
 *
 * <p>Primitive fields, child compounds, and passenger lists are captured as
 * lightweight writer nodes and encoded directly to the MattMC NBT tape format.
 * Codec-backed fields still materialize Java {@link Tag} subtrees through
 * {@link NbtOps}, because those codecs are registry-aware Java policy.
 */
public final class NativeEntityValueOutput implements ValueOutput {
	private static final int MAGIC = 0x5442544E;
	private final ProblemReporter problemReporter;
	private final DynamicOps<Tag> ops;
	private final CompoundNode output;
	private final Metrics metrics;

	private NativeEntityValueOutput(ProblemReporter problemReporter, DynamicOps<Tag> ops, CompoundNode output, Metrics metrics) {
		this.problemReporter = problemReporter;
		this.ops = ops;
		this.output = output;
		this.metrics = metrics;
	}

	public static NativeEntityValueOutput createWithContext(ProblemReporter problemReporter, HolderLookup.Provider provider) {
		return new NativeEntityValueOutput(problemReporter, provider.createSerializationContext(NbtOps.INSTANCE), new CompoundNode(), new Metrics());
	}

	public byte[] buildTape() throws IOException {
		TapeWriter writer = new TapeWriter(Math.max(64, this.output.estimatedBytes() * 2));
		writer.writeIntLE(MAGIC);
		writer.writeShortLE(1);
		writer.writeShortLE(0);
		writer.writeTag("", this.output);
		return writer.toByteArray();
	}

	public CompoundTag buildDebugTag() {
		return (CompoundTag)this.output.toTag();
	}

	public long codecSubtreeNanos() {
		return this.metrics.codecSubtreeNanos;
	}

	public long codecSubtreeMaterializations() {
		return this.metrics.codecSubtreeMaterializations;
	}

	@Override
	public <T> void store(String name, Codec<T> codec, T object) {
		long started = System.nanoTime();
		try {
			switch (codec.encodeStart(this.ops, object)) {
				case Success<Tag> success:
					this.output.put(name, Node.fromTag(success.value()));
					break;
				case Error<Tag> error:
					this.problemReporter.report(new TagValueOutput.EncodeToFieldFailedProblem(name, object, error));
					error.partialValue().ifPresent(tag -> this.output.put(name, Node.fromTag(tag)));
					break;
				default:
					throw new MatchException(null, null);
			}
		} finally {
			this.metrics.recordCodecSubtree(System.nanoTime() - started);
		}
	}

	@Override
	public <T> void storeNullable(String name, Codec<T> codec, @Nullable T object) {
		if (object != null) {
			this.store(name, codec, object);
		}
	}

	@Override
	public <T> void store(MapCodec<T> mapCodec, T object) {
		long started = System.nanoTime();
		try {
			switch (mapCodec.encoder().encodeStart(this.ops, object)) {
				case Success<Tag> success:
					this.output.mergeCompound(success.value());
					break;
				case Error<Tag> error:
					this.problemReporter.report(new TagValueOutput.EncodeToMapFailedProblem(object, error));
					error.partialValue().ifPresent(this.output::mergeCompound);
					break;
				default:
					throw new MatchException(null, null);
			}
		} finally {
			this.metrics.recordCodecSubtree(System.nanoTime() - started);
		}
	}

	@Override
	public void putBoolean(String name, boolean value) {
		this.putByte(name, (byte)(value ? 1 : 0));
	}

	@Override
	public void putByte(String name, byte value) {
		this.output.put(name, new ScalarNode(Tag.TAG_BYTE, value));
	}

	@Override
	public void putShort(String name, short value) {
		this.output.put(name, new ScalarNode(Tag.TAG_SHORT, value));
	}

	@Override
	public void putInt(String name, int value) {
		this.output.put(name, new ScalarNode(Tag.TAG_INT, value));
	}

	@Override
	public void putLong(String name, long value) {
		this.output.put(name, new ScalarNode(Tag.TAG_LONG, value));
	}

	@Override
	public void putFloat(String name, float value) {
		this.output.put(name, new ScalarNode(Tag.TAG_FLOAT, Float.floatToRawIntBits(value)));
	}

	@Override
	public void putDouble(String name, double value) {
		this.output.put(name, new ScalarNode(Tag.TAG_DOUBLE, Double.doubleToRawLongBits(value)));
	}

	@Override
	public void putString(String name, String value) {
		this.output.put(name, new StringNode(value));
	}

	@Override
	public void putIntArray(String name, int[] values) {
		this.output.put(name, new IntArrayNode(values.clone()));
	}

	@Override
	public ValueOutput child(String name) {
		CompoundNode child = new CompoundNode();
		this.output.put(name, child);
		return new NativeEntityValueOutput(this.problemReporter.forChild(new ProblemReporter.FieldPathElement(name)), this.ops, child, this.metrics);
	}

	@Override
	public ValueOutput.ValueOutputList childrenList(String name) {
		ListNode list = new ListNode();
		this.output.put(name, list);
		return new CompoundListOutput(name, this.problemReporter, this.ops, list, this.metrics);
	}

	@Override
	public <T> ValueOutput.TypedOutputList<T> list(String name, Codec<T> codec) {
		ListNode list = new ListNode();
		this.output.put(name, list);
		return new TypedListOutput<>(this.problemReporter, name, this.ops, codec, list, this.metrics);
	}

	@Override
	public void discard(String name) {
		this.output.remove(name);
	}

	@Override
	public boolean isEmpty() {
		return this.output.isEmpty();
	}

	private static final class CompoundListOutput implements ValueOutput.ValueOutputList {
		private final String fieldName;
		private final ProblemReporter problemReporter;
		private final DynamicOps<Tag> ops;
		private final ListNode output;
		private final Metrics metrics;

		private CompoundListOutput(String fieldName, ProblemReporter problemReporter, DynamicOps<Tag> ops, ListNode output, Metrics metrics) {
			this.fieldName = fieldName;
			this.problemReporter = problemReporter;
			this.ops = ops;
			this.output = output;
			this.metrics = metrics;
		}

		@Override
		public ValueOutput addChild() {
			CompoundNode child = new CompoundNode();
			this.output.add(child);
			return new NativeEntityValueOutput(
				this.problemReporter.forChild(new ProblemReporter.IndexedFieldPathElement(this.fieldName, this.output.size() - 1)),
				this.ops,
				child,
				this.metrics
			);
		}

		@Override
		public void discardLast() {
			this.output.discardLast();
		}

		@Override
		public boolean isEmpty() {
			return this.output.isEmpty();
		}
	}

	private static final class TypedListOutput<T> implements ValueOutput.TypedOutputList<T> {
		private final ProblemReporter problemReporter;
		private final String name;
		private final DynamicOps<Tag> ops;
		private final Codec<T> codec;
		private final ListNode output;
		private final Metrics metrics;

		private TypedListOutput(ProblemReporter problemReporter, String name, DynamicOps<Tag> ops, Codec<T> codec, ListNode output, Metrics metrics) {
			this.problemReporter = problemReporter;
			this.name = name;
			this.ops = ops;
			this.codec = codec;
			this.output = output;
			this.metrics = metrics;
		}

		@Override
		public void add(T object) {
			long started = System.nanoTime();
			try {
				switch (this.codec.encodeStart(this.ops, object)) {
					case Success<Tag> success:
						this.output.add(Node.fromTag(success.value()));
						break;
					case Error<Tag> error:
						this.problemReporter.report(new TagValueOutput.EncodeToListFailedProblem(this.name, object, error));
						error.partialValue().ifPresent(tag -> this.output.add(Node.fromTag(tag)));
						break;
					default:
						throw new MatchException(null, null);
				}
			} finally {
				this.metrics.recordCodecSubtree(System.nanoTime() - started);
			}
		}

		@Override
		public boolean isEmpty() {
			return this.output.isEmpty();
		}
	}

	private static final class Metrics {
		private long codecSubtreeNanos;
		private long codecSubtreeMaterializations;

		private void recordCodecSubtree(long nanos) {
			this.codecSubtreeNanos += nanos;
			this.codecSubtreeMaterializations++;
		}
	}

	private sealed interface Node permits ScalarNode, StringNode, ByteArrayNode, IntArrayNode, LongArrayNode, ListNode, CompoundNode {
		byte id();

		void writePayload(TapeWriter writer) throws IOException;

		int count();

		long scalar();

		Tag toTag();

		default int estimatedBytes() {
			return 32;
		}

		static Node fromTag(Tag tag) {
			return switch (tag.getId()) {
				case Tag.TAG_BYTE -> new ScalarNode(Tag.TAG_BYTE, ((ByteTag)tag).value());
				case Tag.TAG_SHORT -> new ScalarNode(Tag.TAG_SHORT, ((ShortTag)tag).value());
				case Tag.TAG_INT -> new ScalarNode(Tag.TAG_INT, ((IntTag)tag).value());
				case Tag.TAG_LONG -> new ScalarNode(Tag.TAG_LONG, ((LongTag)tag).value());
				case Tag.TAG_FLOAT -> new ScalarNode(Tag.TAG_FLOAT, Float.floatToRawIntBits(((FloatTag)tag).value()));
				case Tag.TAG_DOUBLE -> new ScalarNode(Tag.TAG_DOUBLE, Double.doubleToRawLongBits(((DoubleTag)tag).value()));
				case Tag.TAG_BYTE_ARRAY -> new ByteArrayNode(((ByteArrayTag)tag).getAsByteArray());
				case Tag.TAG_STRING -> new StringNode(((StringTag)tag).value());
				case Tag.TAG_LIST -> ListNode.from((ListTag)tag);
				case Tag.TAG_COMPOUND -> CompoundNode.from((CompoundTag)tag);
				case Tag.TAG_INT_ARRAY -> new IntArrayNode(((IntArrayTag)tag).getAsIntArray());
				case Tag.TAG_LONG_ARRAY -> new LongArrayNode(((LongArrayTag)tag).getAsLongArray());
				default -> throw new IllegalArgumentException("Unsupported tag id for native entity output: " + tag.getId());
			};
		}
	}

	private record ScalarNode(byte id, long scalar) implements Node {
		@Override
		public int count() {
			return 0;
		}

		@Override
		public void writePayload(TapeWriter writer) {
		}

		@Override
		public Tag toTag() {
			return switch (this.id) {
				case Tag.TAG_BYTE -> ByteTag.valueOf((byte)this.scalar);
				case Tag.TAG_SHORT -> ShortTag.valueOf((short)this.scalar);
				case Tag.TAG_INT -> IntTag.valueOf((int)this.scalar);
				case Tag.TAG_LONG -> LongTag.valueOf(this.scalar);
				case Tag.TAG_FLOAT -> FloatTag.valueOf(Float.intBitsToFloat((int)this.scalar));
				case Tag.TAG_DOUBLE -> DoubleTag.valueOf(Double.longBitsToDouble(this.scalar));
				default -> throw new IllegalStateException("Unsupported scalar tag id " + this.id);
			};
		}
	}

	private record StringNode(String value) implements Node {
		@Override
		public byte id() {
			return Tag.TAG_STRING;
		}

		@Override
		public int count() {
			return this.value.length();
		}

		@Override
		public long scalar() {
			return 0L;
		}

		@Override
		public void writePayload(TapeWriter writer) {
			writer.writeStringUnits(this.value);
		}

		@Override
		public Tag toTag() {
			return StringTag.valueOf(this.value);
		}
	}

	private record ByteArrayNode(byte[] values) implements Node {
		private ByteArrayNode {
			values = values.clone();
		}

		@Override
		public byte id() {
			return Tag.TAG_BYTE_ARRAY;
		}

		@Override
		public int count() {
			return this.values.length;
		}

		@Override
		public long scalar() {
			return 0L;
		}

		@Override
		public void writePayload(TapeWriter writer) {
			writer.writeBytes(this.values);
		}

		@Override
		public Tag toTag() {
			return new ByteArrayTag(this.values);
		}
	}

	private record IntArrayNode(int[] values) implements Node {
		private IntArrayNode {
			values = values.clone();
		}

		@Override
		public byte id() {
			return Tag.TAG_INT_ARRAY;
		}

		@Override
		public int count() {
			return this.values.length;
		}

		@Override
		public long scalar() {
			return 0L;
		}

		@Override
		public void writePayload(TapeWriter writer) {
			for (int value : this.values) {
				writer.writeIntLE(value);
			}
		}

		@Override
		public Tag toTag() {
			return new IntArrayTag(this.values);
		}
	}

	private record LongArrayNode(long[] values) implements Node {
		private LongArrayNode {
			values = values.clone();
		}

		@Override
		public byte id() {
			return Tag.TAG_LONG_ARRAY;
		}

		@Override
		public int count() {
			return this.values.length;
		}

		@Override
		public long scalar() {
			return 0L;
		}

		@Override
		public void writePayload(TapeWriter writer) {
			for (long value : this.values) {
				writer.writeLongLE(value);
			}
		}

		@Override
		public Tag toTag() {
			return new LongArrayTag(this.values);
		}
	}

	private static final class ListNode implements Node {
		private final List<Node> children = new ArrayList<>();

		static ListNode from(ListTag tag) {
			ListNode node = new ListNode();
			for (Tag child : tag) {
				node.add(Node.fromTag(child));
			}
			return node;
		}

		void add(Node node) {
			this.children.add(node);
		}

		void discardLast() {
			if (!this.children.isEmpty()) {
				this.children.removeLast();
			}
		}

		boolean isEmpty() {
			return this.children.isEmpty();
		}

		int size() {
			return this.children.size();
		}

		byte elementId() {
			byte elementId = Tag.TAG_END;
			for (Node child : this.children) {
				byte childId = child.id();
				if (elementId == Tag.TAG_END) {
					elementId = childId;
				} else if (elementId != childId) {
					return Tag.TAG_COMPOUND;
				}
			}
			return elementId;
		}

		@Override
		public byte id() {
			return Tag.TAG_LIST;
		}

		@Override
		public int count() {
			return this.children.size();
		}

		@Override
		public long scalar() {
			return 0L;
		}

		@Override
		public void writePayload(TapeWriter writer) throws IOException {
			byte elementId = this.elementId();
			for (Node child : this.children) {
				writer.writeTag("", wrapListElement(elementId, child));
			}
		}

		@Override
		public Tag toTag() {
			ListTag tag = new ListTag();
			for (Node child : this.children) {
				tag.add(child.toTag());
			}
			return tag;
		}
	}

	private static final class CompoundNode implements Node {
		private final LinkedHashMap<String, Node> children = new LinkedHashMap<>();

		static CompoundNode from(CompoundTag tag) {
			CompoundNode node = new CompoundNode();
			for (Map.Entry<String, Tag> entry : tag.entrySet()) {
				node.put(entry.getKey(), Node.fromTag(entry.getValue()));
			}
			return node;
		}

		void put(String name, Node node) {
			this.children.put(name, node);
		}

		void remove(String name) {
			this.children.remove(name);
		}

		void mergeCompound(Tag tag) {
			if (!(tag instanceof CompoundTag compound)) {
				throw new IllegalArgumentException("MapCodec encoded a non-compound tag: " + tag.getId());
			}
			for (Map.Entry<String, Tag> entry : compound.entrySet()) {
				this.put(entry.getKey(), Node.fromTag(entry.getValue()));
			}
		}

		boolean isEmpty() {
			return this.children.isEmpty();
		}

		@Override
		public byte id() {
			return Tag.TAG_COMPOUND;
		}

		@Override
		public int count() {
			return this.children.size();
		}

		@Override
		public long scalar() {
			return 0L;
		}

		@Override
		public void writePayload(TapeWriter writer) throws IOException {
			for (Map.Entry<String, Node> entry : this.children.entrySet()) {
				writer.writeTag(entry.getKey(), entry.getValue());
			}
		}

		@Override
		public Tag toTag() {
			CompoundTag tag = new CompoundTag();
			for (Map.Entry<String, Node> entry : this.children.entrySet()) {
				tag.put(entry.getKey(), entry.getValue().toTag());
			}
			return tag;
		}

		@Override
		public int estimatedBytes() {
			int estimate = 32;
			for (Map.Entry<String, Node> entry : this.children.entrySet()) {
				estimate += 24 + entry.getKey().length() * 2 + entry.getValue().estimatedBytes();
			}
			return estimate;
		}
	}

	private static Node wrapListElement(byte rawElementType, Node node) {
		if (rawElementType != Tag.TAG_COMPOUND || node instanceof CompoundNode compound && !isWrapper(compound)) {
			return node;
		}
		CompoundNode wrapper = new CompoundNode();
		wrapper.put("", node);
		return wrapper;
	}

	private static boolean isWrapper(CompoundNode tag) {
		return tag.children.size() == 1 && tag.children.containsKey("");
	}

	private static final class TapeWriter {
		private final ByteArrayOutputStream output;

		private TapeWriter(int initialCapacity) {
			this.output = new ByteArrayOutputStream(initialCapacity);
		}

		void writeTag(String name, Node node) throws IOException {
			byte listElementId = node instanceof ListNode list ? list.elementId() : Tag.TAG_END;
			this.writeRecord(node.id(), listElementId, name.length(), node.count(), node.scalar());
			this.writeStringUnits(name);
			node.writePayload(this);
		}

		private void writeRecord(byte tagId, byte listElementId, int nameUnits, int count, long scalar) {
			this.output.write(tagId);
			this.output.write(listElementId);
			this.writeShortLE(0);
			this.writeIntLE(nameUnits);
			this.writeIntLE(count);
			this.writeIntLE(0);
			this.writeLongLE(scalar);
		}

		private void writeBytes(byte[] bytes) {
			this.output.writeBytes(bytes);
		}

		private void writeStringUnits(String value) {
			for (int i = 0; i < value.length(); i++) {
				this.writeShortLE(value.charAt(i));
			}
		}

		private void writeShortLE(int value) {
			this.output.write(value & 0xFF);
			this.output.write(value >>> 8 & 0xFF);
		}

		private void writeIntLE(int value) {
			this.output.write(value & 0xFF);
			this.output.write(value >>> 8 & 0xFF);
			this.output.write(value >>> 16 & 0xFF);
			this.output.write(value >>> 24 & 0xFF);
		}

		private void writeLongLE(long value) {
			this.writeIntLE((int)value);
			this.writeIntLE((int)(value >>> 32));
		}

		private byte[] toByteArray() {
			return this.output.toByteArray();
		}
	}
}
