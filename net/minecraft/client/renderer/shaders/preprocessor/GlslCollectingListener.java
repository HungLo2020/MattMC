package net.minecraft.client.renderer.shaders.preprocessor;

import org.anarres.cpp.DefaultPreprocessorListener;
import org.anarres.cpp.LexerException;
import org.anarres.cpp.Source;

/**
 * GLSL preprocessor listener for collecting version and extension directives.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/shaderpack/preprocessor/GlslCollectingListener.java
 */
public class GlslCollectingListener extends DefaultPreprocessorListener {
	public static final String VERSION_MARKER = "#warning IRIS_JCPP_GLSL_VERSION";
	public static final String EXTENSION_MARKER = "#warning IRIS_JCPP_GLSL_EXTENSION";

	private final StringBuilder builder;

	public GlslCollectingListener() {
		this.builder = new StringBuilder();
	}

	@Override
	public void handleWarning(Source source, int line, int column, String msg) throws LexerException {
		if (msg.startsWith(VERSION_MARKER)) {
			builder.append(msg.replace(VERSION_MARKER, "#version "));
			builder.append('\n');
		} else if (msg.startsWith(EXTENSION_MARKER)) {
			builder.append(msg.replace(EXTENSION_MARKER, "#extension "));
			builder.append('\n');
		} else {
			super.handleWarning(source, line, column, msg);
		}
	}

	public String collectLines() {
		return builder.toString();
	}
}
