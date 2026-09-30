package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.levelgen.synth.NativeDensityMath;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Collects operands in the original context order; Rust owns all sampling math. */
final class DensityBatch {
    /** Terrain traversal: forIndex does not mutate input buffers or reenter fillArray. */
    interface Provider extends DensityFunction.ContextProvider {}
    private static final ThreadLocal<ArithmeticScratch> ARITHMETIC = ThreadLocal.withInitial(ArithmeticScratch::new);

    static final class ArithmeticScratch {
        byte[] mask = new byte[0];
        double[] right = new double[0];
        double[] left = new double[0];
        final DensityBatch inside = new DensityBatch(), outside = new DensityBatch();
        void ensureCapacity(int size) {
            if (mask.length < size) mask = new byte[size];
            if (right.length < size) right = new double[size];
            if (left.length < size) left = new double[size];
        }
    }

    /** Borrowed only after input fills finish and for known, non-reentrant children. */
    static ArithmeticScratch arithmeticScratch(int size) {
        ArithmeticScratch scratch = size <= 4096 ? ARITHMETIC.get() : new ArithmeticScratch();
        scratch.ensureCapacity(size);
        return scratch;
    }
    private static boolean leaf(DensityFunction f) {
        return f instanceof DensityFunctions.Noise || f instanceof DensityFunctions.ShiftNoise || f instanceof DensityFunctions.Constant;
    }
    /** Only leaves with no child visits: gather selected points in the original
     * interleaved order, then SIMD-sample each stream without touching caches. */
    static boolean rangeChoice(double[] values,DensityFunction.ContextProvider provider,DensityFunction inside,
                               DensityFunction outside,double min,double max) {
        if(!(provider instanceof Provider) || values.length<4 || !leaf(inside) || !leaf(outside))return false;
        if(inside instanceof DensityFunctions.Constant && outside instanceof DensityFunctions.Constant)return false;
        var scratch=arithmeticScratch(values.length);
        var inConstant=inside instanceof DensityFunctions.Constant c?c:null;
        var outConstant=outside instanceof DensityFunctions.Constant c?c:null;
        if(inConstant==null && !scratch.inside.prepare(inside,values.length))return false;
        if(outConstant==null && !scratch.outside.prepare(outside,values.length))return false;
        NativeDensityMath.mask(22,values,scratch.mask,min,max);
        int inCount=0,outCount=0;
        for(int i=0;i<values.length;i++) {
            var context=provider.forIndex(i);
            if(scratch.mask[i]!=0) {
                if(inConstant==null)scratch.inside.record(inCount++,context);
            } else if(outConstant==null)scratch.outside.record(outCount++,context);
        }
        if(inCount!=0)scratch.inside.finish(scratch.left,inCount);
        if(outCount!=0)scratch.outside.finish(scratch.right,outCount);
        inCount=outCount=0;
        for(int i=0;i<values.length;i++) {
            values[i]=scratch.mask[i]!=0 ? (inConstant!=null?inConstant.value():scratch.left[inCount++])
                : (outConstant!=null?outConstant.value():scratch.right[outCount++]);
        }
        return true;
    }
    private double[] operands = new double[0];
    private DensityFunction function;
    private NormalNoise noise;
    private BlendedNoise blended;
    private int operation;
    private double xzScale, yScale;

    boolean prepare(DensityFunction function, int count) {
        if (count < 4) return false;
        function = NativeDensity.unwrap(function);
        blended = null;
        if (function.getClass() == BlendedNoise.class) {
            int length = Math.multiplyExact(count, 3);
            if (operands.length < length) operands = new double[length];
            this.function = function;
            this.blended = (BlendedNoise) function;
            return true;
        }
        DensityFunction.NoiseHolder holder;
        xzScale = yScale = 0.0;
        if (function instanceof DensityFunctions.Noise n) {
            holder = n.noise(); operation = 1; xzScale = n.xzScale(); yScale = n.yScale();
        } else if (function instanceof DensityFunctions.ShiftNoise n) {
            holder = n.offsetNoise(); operation = 2;
        } else if (function instanceof DensityFunctions.ShiftedNoise n && NativeDensity.safeInputs(n)) {
            holder = n.noise(); operation = 5; xzScale = n.xzScale(); yScale = n.yScale();
        } else if (function instanceof DensityFunctions.WeirdScaledSampler n && NativeDensity.safeInputs(n.input())) {
            holder = n.noise(); operation = n.rarityValueMapper() == DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1 ? 6 : 7;
        } else return false;
        if (holder.noise() == null) return false;
        int length = Math.multiplyExact(count, 6);
        if (operands.length < length) operands = new double[length];
        this.function = function;
        this.noise = holder.noise();
        return true;
    }

    void record(int i, DensityFunction.FunctionContext c) {
        if (blended != null) {
            int at = i * 3;
            operands[at] = c.blockX(); operands[at+1] = c.blockY(); operands[at+2] = c.blockZ();
            return;
        }
        int at = i * 6;
        if (operation == 2) {
            // Preserve accessor order and the original axis routing, including ShiftB.
            operands[at] = function instanceof DensityFunctions.ShiftB ? c.blockZ() : c.blockX();
            operands[at+1] = function instanceof DensityFunctions.ShiftB ? c.blockX() : function instanceof DensityFunctions.ShiftA ? 0.0 : c.blockY();
            operands[at+2] = function instanceof DensityFunctions.ShiftB ? 0.0 : c.blockZ();
        } else if (operation == 5) {
            var n = (DensityFunctions.ShiftedNoise) function;
            operands[at] = c.blockX(); operands[at+3] = n.shiftX().compute(c);
            operands[at+1] = c.blockY(); operands[at+4] = n.shiftY().compute(c);
            operands[at+2] = c.blockZ(); operands[at+5] = n.shiftZ().compute(c);
        } else if (operation == 6 || operation == 7) {
            recordRarity(i, c, ((DensityFunctions.WeirdScaledSampler) function).input().compute(c));
        } else {
            operands[at] = c.blockX(); operands[at+1] = c.blockY(); operands[at+2] = c.blockZ();
        }
    }

    void recordRarity(int i, DensityFunction.FunctionContext c, double input) {
        int at = i * 6;
        operands[at] = c.blockX(); operands[at+1] = c.blockY(); operands[at+2] = c.blockZ(); operands[at+3] = input;
    }

    void finish(double[] output) {
        if (blended != null) { blended.fillNative(operands, output); return; }
        NativeDensityMath.noiseArray(noise, operation, operands, output, xzScale, yScale);
    }
    void finish(double[] output,int count) {
        if (blended != null) { blended.fillNative(operands, output, count); return; }
        NativeDensityMath.noiseArray(noise, operation, operands, output, count, xzScale, yScale);
    }

    static boolean fillRarity(DensityFunctions.WeirdScaledSampler function, double[] output, DensityFunction.ContextProvider provider) {
        if (!(provider instanceof Provider) || output.length < 4 || !NativeDensity.safeInputs(function.input())) return false;
        var batch = new DensityBatch();
        if (!batch.prepare(function, output.length)) return false;
        function.input().fillArray(output, provider);
        for (int i = 0; i < output.length; i++) batch.recordRarity(i, provider.forIndex(i), output[i]);
        batch.finish(output);
        return true;
    }
}
