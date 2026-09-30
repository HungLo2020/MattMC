package net.irisshaders.iris.gl.blending;

public record AlphaTest(AlphaTestFunction function, float reference) {
	public static final AlphaTest ALWAYS = new AlphaTest(AlphaTestFunction.ALWAYS, 0.0f);

	// WARNING: adding new fields requires updating hashCode and equals methods!


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AlphaTest other = (AlphaTest) obj;
		if (function != other.function)
			return false;
		return Float.floatToIntBits(reference) == Float.floatToIntBits(other.reference);
	}
}
