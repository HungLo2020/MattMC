package net.minecraft.world.level.border;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WorldBorder extends SavedData {
	public static final double MAX_SIZE = 5.999997E7F;
	public static final double MAX_CENTER_COORDINATE = 2.9999984E7;
	public static final Codec<WorldBorder> CODEC = WorldBorder.Settings.CODEC.xmap(WorldBorder.Settings::toWorldBorder, WorldBorder.Settings::new);
	public static final SavedDataType<WorldBorder> TYPE = new SavedDataType<>(
		"world_border", context -> WorldBorder.Settings.DEFAULT.toWorldBorder(), context -> CODEC, DataFixTypes.SAVED_DATA_WORLD_BORDER
	);
	private final List<BorderChangeListener> listeners = Lists.<BorderChangeListener>newArrayList();
	double damagePerBlock = 0.2;
	double safeZone = 5.0;
	int warningTime = 15;
	int warningBlocks = 5;
	double centerX;
	double centerZ;
	int absoluteMaxSize = 29999984;
	WorldBorder.BorderExtent extent = new WorldBorder.StaticBorderExtent(5.999997E7F);

	public boolean isWithinBounds(BlockPos blockPos) {
		return this.isWithinBounds(blockPos.getX(), blockPos.getZ());
	}

	public boolean isWithinBounds(Vec3 vec3) {
		return this.isWithinBounds(vec3.x, vec3.z);
	}

	public boolean isWithinBounds(ChunkPos chunkPos) {
		return this.isWithinBounds(chunkPos.getMinBlockX(), chunkPos.getMinBlockZ()) && this.isWithinBounds(chunkPos.getMaxBlockX(), chunkPos.getMaxBlockZ());
	}

	public boolean isWithinBounds(AABB aABB) {
		return this.isWithinBounds(aABB.minX, aABB.minZ, aABB.maxX - 1.0E-5F, aABB.maxZ - 1.0E-5F);
	}

	private boolean isWithinBounds(double d, double e, double f, double g) {
		return this.isWithinBounds(d, e) && this.isWithinBounds(f, g);
	}

	public boolean isWithinBounds(double d, double e) {
		return this.isWithinBounds(d, e, 0.0);
	}

	public boolean isWithinBounds(double d, double e, double f) {
		return d >= this.getMinX() - f && d < this.getMaxX() + f && e >= this.getMinZ() - f && e < this.getMaxZ() + f;
	}

	public BlockPos clampToBounds(BlockPos blockPos) {
		return this.clampToBounds(blockPos.getX(), blockPos.getY(), blockPos.getZ());
	}

	public BlockPos clampToBounds(Vec3 vec3) {
		return this.clampToBounds(vec3.x(), vec3.y(), vec3.z());
	}

	public BlockPos clampToBounds(double d, double e, double f) {
		return BlockPos.containing(this.clampVec3ToBound(d, e, f));
	}

	public Vec3 clampVec3ToBound(Vec3 vec3) {
		return this.clampVec3ToBound(vec3.x, vec3.y, vec3.z);
	}

	public Vec3 clampVec3ToBound(double d, double e, double f) {
		return new Vec3(Mth.clamp(d, this.getMinX(), this.getMaxX() - 1.0E-5F), e, Mth.clamp(f, this.getMinZ(), this.getMaxZ() - 1.0E-5F));
	}

	public double getDistanceToBorder(Entity entity) {
		return this.getDistanceToBorder(entity.getX(), entity.getZ());
	}

	public VoxelShape getCollisionShape() {
		return this.extent.getCollisionShape();
	}

	public double getDistanceToBorder(double d, double e) {
		double f = e - this.getMinZ();
		double g = this.getMaxZ() - e;
		double h = d - this.getMinX();
		double i = this.getMaxX() - d;
		double j = Math.min(h, i);
		j = Math.min(j, f);
		return Math.min(j, g);
	}

	public boolean isInsideCloseToBorder(Entity entity, AABB aABB) {
		double d = Math.max(Mth.absMax(aABB.getXsize(), aABB.getZsize()), 1.0);
		return this.getDistanceToBorder(entity) < d * 2.0 && this.isWithinBounds(entity.getX(), entity.getZ(), d);
	}

	public BorderStatus getStatus() {
		return this.extent.getStatus();
	}

	public double getMinX() {
		return this.extent.getMinX();
	}

	public double getMinZ() {
		return this.extent.getMinZ();
	}

	public double getMaxX() {
		return this.extent.getMaxX();
	}

	public double getMaxZ() {
		return this.extent.getMaxZ();
	}

	public double getCenterX() {
		return this.centerX;
	}

	public double getCenterZ() {
		return this.centerZ;
	}

	public void setCenter(double d, double e) {
		this.centerX = d;
		this.centerZ = e;
		this.extent.onCenterChange();
		this.setDirty();

		for (BorderChangeListener borderChangeListener : this.getListeners()) {
			borderChangeListener.onSetCenter(this, d, e);
		}
	}

	public double getSize() {
		return this.extent.getSize();
	}

	public long getLerpTime() {
		return this.extent.getLerpTime();
	}

	public double getLerpTarget() {
		return this.extent.getLerpTarget();
	}

	public void setSize(double d) {
		this.extent = new WorldBorder.StaticBorderExtent(d);
		this.setDirty();

		for (BorderChangeListener borderChangeListener : this.getListeners()) {
			borderChangeListener.onSetSize(this, d);
		}
	}

	public void lerpSizeBetween(double d, double e, long l) {
		this.extent = (WorldBorder.BorderExtent)(d == e ? new WorldBorder.StaticBorderExtent(e) : new WorldBorder.MovingBorderExtent(d, e, l));
		this.setDirty();

		for (BorderChangeListener borderChangeListener : this.getListeners()) {
			borderChangeListener.onLerpSize(this, d, e, l);
		}
	}

	protected List<BorderChangeListener> getListeners() {
		return Lists.<BorderChangeListener>newArrayList(this.listeners);
	}

	public void addListener(BorderChangeListener borderChangeListener) {
		this.listeners.add(borderChangeListener);
	}

	public void removeListener(BorderChangeListener borderChangeListener) {
		this.listeners.remove(borderChangeListener);
	}

	public void setAbsoluteMaxSize(int i) {
		this.absoluteMaxSize = i;
		this.extent.onAbsoluteMaxSizeChange();
	}

	public int getAbsoluteMaxSize() {
		return this.absoluteMaxSize;
	}

	public double getSafeZone() {
		return this.safeZone;
	}

	public void setSafeZone(double d) {
		this.safeZone = d;
		this.setDirty();

		for (BorderChangeListener borderChangeListener : this.getListeners()) {
			borderChangeListener.onSetSafeZone(this, d);
		}
	}

	public double getDamagePerBlock() {
		return this.damagePerBlock;
	}

	public void setDamagePerBlock(double d) {
		this.damagePerBlock = d;
		this.setDirty();

		for (BorderChangeListener borderChangeListener : this.getListeners()) {
			borderChangeListener.onSetDamagePerBlock(this, d);
		}
	}

	public double getLerpSpeed() {
		return this.extent.getLerpSpeed();
	}

	public int getWarningTime() {
		return this.warningTime;
	}

	public void setWarningTime(int i) {
		this.warningTime = i;
		this.setDirty();

		for (BorderChangeListener borderChangeListener : this.getListeners()) {
			borderChangeListener.onSetWarningTime(this, i);
		}
	}

	public int getWarningBlocks() {
		return this.warningBlocks;
	}

	public void setWarningBlocks(int i) {
		this.warningBlocks = i;
		this.setDirty();

		for (BorderChangeListener borderChangeListener : this.getListeners()) {
			borderChangeListener.onSetWarningBlocks(this, i);
		}
	}

	public void tick() {
		this.extent = this.extent.update();
	}

	public void applySettings(WorldBorder.Settings settings) {
		this.setCenter(settings.centerX(), settings.centerZ());
		this.setDamagePerBlock(settings.damagePerBlock());
		this.setSafeZone(settings.safeZone());
		this.setWarningBlocks(settings.warningBlocks());
		this.setWarningTime(settings.warningTime());
		if (settings.lerpTime() > 0L) {
			this.lerpSizeBetween(settings.size(), settings.lerpTarget(), settings.lerpTime());
		} else {
			this.setSize(settings.size());
		}
	}

	interface BorderExtent {
		double getMinX();

		double getMaxX();

		double getMinZ();

		double getMaxZ();

		double getSize();

		double getLerpSpeed();

		long getLerpTime();

		double getLerpTarget();

		BorderStatus getStatus();

		void onAbsoluteMaxSizeChange();

		void onCenterChange();

		WorldBorder.BorderExtent update();

		VoxelShape getCollisionShape();
	}

	class MovingBorderExtent implements WorldBorder.BorderExtent {
		private final double from;
		private final double to;
		private final long lerpEnd;
		private final long lerpBegin;
		private final double lerpDuration;

		MovingBorderExtent(final double d, final double e, final long l) {
			this.from = d;
			this.to = e;
			this.lerpDuration = l;
			this.lerpBegin = Util.getMillis();
			this.lerpEnd = this.lerpBegin + l;
		}

		@Override
		public double getMinX() {
			return Mth.clamp(WorldBorder.this.getCenterX() - this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
		}

		@Override
		public double getMinZ() {
			return Mth.clamp(WorldBorder.this.getCenterZ() - this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
		}

		@Override
		public double getMaxX() {
			return Mth.clamp(WorldBorder.this.getCenterX() + this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
		}

		@Override
		public double getMaxZ() {
			return Mth.clamp(WorldBorder.this.getCenterZ() + this.getSize() / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
		}

		@Override
		public double getSize() {
			double d = (Util.getMillis() - this.lerpBegin) / this.lerpDuration;
			return d < 1.0 ? Mth.lerp(d, this.from, this.to) : this.to;
		}

		@Override
		public double getLerpSpeed() {
			return Math.abs(this.from - this.to) / (this.lerpEnd - this.lerpBegin);
		}

		@Override
		public long getLerpTime() {
			return this.lerpEnd - Util.getMillis();
		}

		@Override
		public double getLerpTarget() {
			return this.to;
		}

		@Override
		public BorderStatus getStatus() {
			return this.to < this.from ? BorderStatus.SHRINKING : BorderStatus.GROWING;
		}

		@Override
		public void onCenterChange() {
		}

		@Override
		public void onAbsoluteMaxSizeChange() {
		}

		@Override
		public WorldBorder.BorderExtent update() {
			if (this.getLerpTime() <= 0L) {
				WorldBorder.this.setDirty();
				return WorldBorder.this.new StaticBorderExtent(this.to);
			} else {
				return this;
			}
		}

		@Override
		public VoxelShape getCollisionShape() {
			return Shapes.join(
				Shapes.INFINITY,
				Shapes.box(
					Math.floor(this.getMinX()),
					Double.NEGATIVE_INFINITY,
					Math.floor(this.getMinZ()),
					Math.ceil(this.getMaxX()),
					Double.POSITIVE_INFINITY,
					Math.ceil(this.getMaxZ())
				),
				BooleanOp.ONLY_FIRST
			);
		}
	}

	public record Settings(
		double centerX, double centerZ, double damagePerBlock, double safeZone, int warningBlocks, int warningTime, double size, long lerpTime, double lerpTarget
	) {
		public static final WorldBorder.Settings DEFAULT = new WorldBorder.Settings(0.0, 0.0, 0.2, 5.0, 5, 15, 5.999997E7F, 0L, 0.0);
		public static final Codec<WorldBorder.Settings> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Codec.doubleRange(-2.9999984E7, 2.9999984E7).fieldOf("center_x").forGetter(WorldBorder.Settings::centerX),
					Codec.doubleRange(-2.9999984E7, 2.9999984E7).fieldOf("center_z").forGetter(WorldBorder.Settings::centerZ),
					Codec.DOUBLE.fieldOf("damage_per_block").forGetter(WorldBorder.Settings::damagePerBlock),
					Codec.DOUBLE.fieldOf("safe_zone").forGetter(WorldBorder.Settings::safeZone),
					Codec.INT.fieldOf("warning_blocks").forGetter(WorldBorder.Settings::warningBlocks),
					Codec.INT.fieldOf("warning_time").forGetter(WorldBorder.Settings::warningTime),
					Codec.DOUBLE.fieldOf("size").forGetter(WorldBorder.Settings::size),
					Codec.LONG.fieldOf("lerp_time").forGetter(WorldBorder.Settings::lerpTime),
					Codec.DOUBLE.fieldOf("lerp_target").forGetter(WorldBorder.Settings::lerpTarget)
				)
				.apply(instance, WorldBorder.Settings::new)
		);

		public Settings(WorldBorder worldBorder) {
			this(
				worldBorder.centerX,
				worldBorder.centerZ,
				worldBorder.damagePerBlock,
				worldBorder.safeZone,
				worldBorder.warningBlocks,
				worldBorder.warningTime,
				worldBorder.extent.getSize(),
				worldBorder.extent.getLerpTime(),
				worldBorder.extent.getLerpTarget()
			);
		}

		public WorldBorder toWorldBorder() {
			WorldBorder worldBorder = new WorldBorder();
			worldBorder.applySettings(this);
			return worldBorder;
		}
	}

	class StaticBorderExtent implements WorldBorder.BorderExtent {
		private final double size;
		private double minX;
		private double minZ;
		private double maxX;
		private double maxZ;
		private VoxelShape shape;

		public StaticBorderExtent(final double d) {
			this.size = d;
			this.updateBox();
		}

		@Override
		public double getMinX() {
			return this.minX;
		}

		@Override
		public double getMaxX() {
			return this.maxX;
		}

		@Override
		public double getMinZ() {
			return this.minZ;
		}

		@Override
		public double getMaxZ() {
			return this.maxZ;
		}

		@Override
		public double getSize() {
			return this.size;
		}

		@Override
		public BorderStatus getStatus() {
			return BorderStatus.STATIONARY;
		}

		@Override
		public double getLerpSpeed() {
			return 0.0;
		}

		@Override
		public long getLerpTime() {
			return 0L;
		}

		@Override
		public double getLerpTarget() {
			return this.size;
		}

		private void updateBox() {
			this.minX = Mth.clamp(WorldBorder.this.getCenterX() - this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
			this.minZ = Mth.clamp(WorldBorder.this.getCenterZ() - this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
			this.maxX = Mth.clamp(WorldBorder.this.getCenterX() + this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
			this.maxZ = Mth.clamp(WorldBorder.this.getCenterZ() + this.size / 2.0, (double)(-WorldBorder.this.absoluteMaxSize), (double)WorldBorder.this.absoluteMaxSize);
			this.shape = Shapes.join(
				Shapes.INFINITY,
				Shapes.box(
					Math.floor(this.getMinX()),
					Double.NEGATIVE_INFINITY,
					Math.floor(this.getMinZ()),
					Math.ceil(this.getMaxX()),
					Double.POSITIVE_INFINITY,
					Math.ceil(this.getMaxZ())
				),
				BooleanOp.ONLY_FIRST
			);
		}

		@Override
		public void onAbsoluteMaxSizeChange() {
			this.updateBox();
		}

		@Override
		public void onCenterChange() {
			this.updateBox();
		}

		@Override
		public WorldBorder.BorderExtent update() {
			return this;
		}

		@Override
		public VoxelShape getCollisionShape() {
			return this.shape;
		}
	}
}
