package net.irisshaders.iris.uniforms.custom.cached;

import net.stareval.function.FunctionReturn;
import net.stareval.function.Type;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.vulkanic.VulkanicAPI;

import java.util.function.BooleanSupplier;

public class BooleanCachedUniform extends CachedUniform {

	final private BooleanSupplier supplier;
	private boolean cached;

	public BooleanCachedUniform(String name, UniformUpdateFrequency updateFrequency, BooleanSupplier supplier) {
		super(name, updateFrequency);
		this.supplier = supplier;
	}

	@Override
	protected boolean doUpdate() {
		boolean prev = this.cached;
		this.cached = this.supplier.getAsBoolean();
		return prev != cached;
	}

	@Override
	public void push(int location) {
		VulkanicAPI.setUniform1i(VulkanicAPI.getImmediateContext(), location, this.cached ? 1 : 0);
	}

	@Override
	public void writeTo(FunctionReturn functionReturn) {
		functionReturn.booleanReturn = this.cached;
	}

	@Override
	public Type getType() {
		return Type.Boolean;
	}
}
