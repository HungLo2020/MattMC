package net.minecraft.world.level.levelgen;

import java.util.Arrays;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public interface Aquifer {
	static Aquifer create(
		NoiseChunk noiseChunk,
		ChunkPos chunkPos,
		NoiseRouter noiseRouter,
		PositionalRandomFactory positionalRandomFactory,
		int i,
		int j,
		Aquifer.FluidPicker fluidPicker
	) {
		return new Aquifer.NoiseBasedAquifer(noiseChunk, chunkPos, noiseRouter, positionalRandomFactory, i, j, fluidPicker);
	}

	static Aquifer createDisabled(Aquifer.FluidPicker fluidPicker) {
		return new Aquifer.Disabled(fluidPicker);
	}

	/** The disabled aquifer, named so the native NOISE fill can recognize its picker. */
	record Disabled(Aquifer.FluidPicker fluidPicker) implements Aquifer {
		@Nullable
		@Override
		public BlockState computeSubstance(DensityFunction.FunctionContext functionContext, double d) {
			return d > 0.0 ? null : this.fluidPicker.computeFluid(functionContext.blockX(), functionContext.blockY(), functionContext.blockZ()).at(functionContext.blockY());
		}

		@Override
		public boolean shouldScheduleFluidUpdate() {
			return false;
		}
	}

	@Nullable
	BlockState computeSubstance(DensityFunction.FunctionContext functionContext, double d);

	boolean shouldScheduleFluidUpdate();

	public interface FluidPicker {
		Aquifer.FluidStatus computeFluid(int i, int j, int k);
	}

	public record FluidStatus(int fluidLevel, BlockState fluidType) {

		public BlockState at(int i) {
			return i < this.fluidLevel ? this.fluidType : Blocks.AIR.defaultBlockState();
		}
	}

	public static class NoiseBasedAquifer implements Aquifer {
		private final NoiseChunk noiseChunk;
		private final DensityFunction barrierNoise;
		private final DensityFunction fluidLevelFloodednessNoise;
		private final DensityFunction fluidLevelSpreadNoise;
		private final DensityFunction lavaNoise;
		private final PositionalRandomFactory positionalRandomFactory;
		private final Aquifer.FluidStatus[] aquiferCache;
		private final long[] aquiferLocationCache;
		private NativeAquifer nativeAquifer;
		private final Aquifer.FluidPicker globalFluidPicker;
		private final DensityFunction erosion;
		private final DensityFunction depth;
		private boolean shouldScheduleFluidUpdate;
		private final int skipSamplingAboveY;
		private final int minGridX;
		private final int minGridY;
		private final int minGridZ;
		private final int gridSizeX;
		private final int gridSizeZ;


		NoiseBasedAquifer(
			NoiseChunk noiseChunk,
			ChunkPos chunkPos,
			NoiseRouter noiseRouter,
			PositionalRandomFactory positionalRandomFactory,
			int i,
			int j,
			Aquifer.FluidPicker fluidPicker
		) {
			this.noiseChunk = noiseChunk;
			this.barrierNoise = noiseRouter.barrierNoise();
			this.fluidLevelFloodednessNoise = noiseRouter.fluidLevelFloodednessNoise();
			this.fluidLevelSpreadNoise = noiseRouter.fluidLevelSpreadNoise();
			this.lavaNoise = noiseRouter.lavaNoise();
			this.erosion = noiseRouter.erosion();
			this.depth = noiseRouter.depth();
			this.positionalRandomFactory = positionalRandomFactory;
			this.minGridX = gridX(chunkPos.getMinBlockX() + -5) + 0;
			this.globalFluidPicker = fluidPicker;
			int k = gridX(chunkPos.getMaxBlockX() + -5) + 1;
			this.gridSizeX = k - this.minGridX + 1;
			this.minGridY = gridY(i + 1) + -1;
			int l = gridY(i + j + 1) + 1;
			int m = l - this.minGridY + 1;
			this.minGridZ = gridZ(chunkPos.getMinBlockZ() + -5) + 0;
			int n = gridZ(chunkPos.getMaxBlockZ() + -5) + 1;
			this.gridSizeZ = n - this.minGridZ + 1;
			int o = this.gridSizeX * m * this.gridSizeZ;
			this.aquiferCache = new Aquifer.FluidStatus[o];
			this.aquiferLocationCache = new long[o];
			Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
			int p = this.adjustSurfaceLevel(
				noiseChunk.maxPreliminarySurfaceLevel(fromGridX(this.minGridX, 0), fromGridZ(this.minGridZ, 0), fromGridX(k, 9), fromGridZ(n, 9))
			);
			int q = gridY(p + 12) - -1;
			this.skipSamplingAboveY = fromGridY(q, 11) - 1;

		}

        NativeAquifer nativeAquifer() {
            if(this.nativeAquifer==null) {
                this.nativeAquifer = new NativeAquifer(this, noiseChunk, aquiferLocationCache,
                    new int[]{minGridX,minGridY,minGridZ,gridSizeX,gridSizeZ},
                    globalFluidPicker, barrierNoise, skipSamplingAboveY, positionalRandomFactory);
            }
            return this.nativeAquifer;
        }

		private int getIndex(int i, int j, int k) {
			int l = i - this.minGridX;
			int m = j - this.minGridY;
			int n = k - this.minGridZ;
			return (m * this.gridSizeZ + n) * this.gridSizeX + l;
		}

		@Nullable
		@Override
		public BlockState computeSubstance(DensityFunction.FunctionContext functionContext, double d) {
            return compute(functionContext,d,false);
        }
        BlockState computeMaterial(DensityFunction.FunctionContext context,double density) {
            return compute(context,density,true);
        }
        private BlockState compute(DensityFunction.FunctionContext functionContext,double d,boolean cachedMaterial) {
			if (d > 0.0) {
				this.shouldScheduleFluidUpdate = false;
				return null;
			} else {
				int i = functionContext.blockX();
				int j = functionContext.blockY();
				int k = functionContext.blockZ();
				Aquifer.FluidStatus fluidStatus = this.globalFluidPicker.computeFluid(i, j, k);
				if (j > this.skipSamplingAboveY) {
					this.shouldScheduleFluidUpdate = false;
					return fluidStatus.at(j);
				} else if (fluidStatus.at(j).is(Blocks.LAVA)) {
					this.shouldScheduleFluidUpdate = false;
					return SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? Blocks.AIR.defaultBlockState() : Blocks.LAVA.defaultBlockState();
				} else {
                    NativeAquifer nativeAquifer = this.nativeAquifer();
                    BlockState result = nativeAquifer.compute(functionContext, d, i, j, k, cachedMaterial);
                    this.shouldScheduleFluidUpdate = nativeAquifer.shouldScheduleFluidUpdate();
                    return result == null || !SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? result : Blocks.AIR.defaultBlockState();
                }
            }
        }

        // Random factories and lazy fluid/noise sources remain Java bindings.
        // Unknown factories visit precisely the original twelve centers in order.
        long[] locationCache() { return aquiferLocationCache; }

        int skipSamplingAboveY() { return skipSamplingAboveY; }

        Aquifer.FluidPicker globalFluidPicker() { return globalFluidPicker; }

        long location(int x, int y, int z) {
            int index = getIndex(x,y,z);
            long value = aquiferLocationCache[index];
            if (value == Long.MAX_VALUE) {
                RandomSource random = positionalRandomFactory.at(x,y,z);
                value = BlockPos.asLong(fromGridX(x,random.nextInt(10)),fromGridY(y,random.nextInt(9)),fromGridZ(z,random.nextInt(10)));
                aquiferLocationCache[index] = value;
            }
            return value;
        }

        void centers(int x, int y, int z, int[] out) {
            int gx=gridX(x-5),gy=gridY(y+1),gz=gridZ(z-5),offset=0;
            for (int dx=0;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=0;dz<=1;dz++) {
                long pos=location(gx+dx,gy+dy,gz+dz);
                out[offset++]=getIndex(gx+dx,gy+dy,gz+dz);
                out[offset++]=BlockPos.getX(pos);out[offset++]=BlockPos.getY(pos);out[offset++]=BlockPos.getZ(pos);
            }
        }

        void cellLocations(int x, int y, int z, int width, int height) {
            for (int gx=gridX(x-5);gx<=gridX(x+width-6)+1;gx++)
                for (int gy=gridY(y+1)-1;gy<=gridY(y+height)+1;gy++)
                    for (int gz=gridZ(z-5);gz<=gridZ(z+width-6)+1;gz++) location(gx,gy,gz);
        }

        boolean lavaBelow(int x, int y, int z) { return globalFluidPicker.computeFluid(x,y-1,z).at(y-1).is(Blocks.LAVA); }
        double barrier(DensityFunction.FunctionContext context) { return barrierNoise.compute(context); }

		@Override
		public boolean shouldScheduleFluidUpdate() {
			return this.shouldScheduleFluidUpdate;
		}


		private static int gridX(int i) {
			return i >> 4;
		}

		private static int fromGridX(int i, int j) {
			return (i << 4) + j;
		}

		private static int gridY(int i) {
			return Math.floorDiv(i, 12);
		}

		private static int fromGridY(int i, int j) {
			return i * 12 + j;
		}

		private static int gridZ(int i) {
			return i >> 4;
		}

		private static int fromGridZ(int i, int j) {
			return (i << 4) + j;
		}

		Aquifer.FluidStatus getAquiferStatus(int i) {
			Aquifer.FluidStatus fluidStatus = this.aquiferCache[i];
			if (fluidStatus != null) {
				return fluidStatus;
			} else {
				long l = this.aquiferLocationCache[i];
				Aquifer.FluidStatus fluidStatus2 = this.computeFluid(BlockPos.getX(l), BlockPos.getY(l), BlockPos.getZ(l));
				this.aquiferCache[i] = fluidStatus2;
				return fluidStatus2;
			}
		}

        private Aquifer.FluidStatus computeFluid(int x,int y,int z) { return nativeAquifer().fluid(x,y,z); }
        Aquifer.FluidStatus fluidSource(int x,int y,int z) { return globalFluidPicker.computeFluid(x,y,z); }
        int surfaceSource(int x,int z) { return noiseChunk.preliminarySurfaceLevel(x,z); }
        double noiseSource(int kind,DensityFunction.SinglePointContext context) {
            return switch(kind) {
                case 3 -> erosion.compute(context);
                case 4 -> depth.compute(context);
                case 5 -> fluidLevelFloodednessNoise.compute(context);
                case 6 -> fluidLevelSpreadNoise.compute(context);
                case 7 -> lavaNoise.compute(context);
                default -> throw new IllegalArgumentException("Unknown aquifer noise request: "+kind);
            };
        }
        private int adjustSurfaceLevel(int i) { return i + 8; }
    }
}
