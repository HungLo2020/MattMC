package net.minecraft.client.renderer.shaders.helpers;

/**
 * An absurdly simple class for storing pairs of strings because Java lacks pair / tuple types.
 * 
 * VERBATIM copy from IRIS.
 * Reference: frnsrc/Iris-1.21.9/common/src/main/java/net/irisshaders/iris/helpers/StringPair.java
 */
public record StringPair(String key, String value) {
}
