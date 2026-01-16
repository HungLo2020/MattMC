package net.fabricmc.loader.language;

import java.io.IOException;

@Deprecated
public interface LanguageAdapter {
	enum MissingSuperclassBehavior {
		RETURN_NULL,
		CRASH
	}

	default Object createInstance(String classString, Options options) throws ClassNotFoundException, LanguageAdapterException {
		try {
			Class<?> c = JavaLanguageAdapter.getClass(classString, options);

			if (c != null) {
				return createInstance(c, options);
			} else {
				return null;
			}
		} catch (IOException e) {
			throw new LanguageAdapterException("I/O error!", e);
		}
	}

	Object createInstance(Class<?> baseClass, Options options) throws LanguageAdapterException;

	class Options {
		private MissingSuperclassBehavior missingSuperclassBehavior;

		public MissingSuperclassBehavior getMissingSuperclassBehavior() {
			return missingSuperclassBehavior;
		}

		public static class Builder {
			private final Options options;

			private Builder() {
				options = new Options();
			}

			public static Builder create() {
				return new Builder();
			}

			public Builder missingSuperclassBehaviour(MissingSuperclassBehavior value) {
				options.missingSuperclassBehavior = value;
				return this;
			}

			public Options build() {
				return options;
			}
		}
	}
}
