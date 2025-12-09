package net.minecraft.client.renderer.shaders.pack;

import net.minecraft.client.renderer.shaders.helpers.OptionalBoolean;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ShaderProperties.
 * Tests IRIS-verbatim property parsing behavior.
 */
class ShaderPropertiesTest {

@Test
void testEmptyProperties() {
// When
ShaderProperties props = ShaderProperties.empty();

// Then - All should be DEFAULT (matching IRIS)
assertThat(props.getOldLighting()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getSun()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getMoon()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getStars()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getWeather()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getUnderwaterOverlay()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getVignette()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getSky()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getShadowEnabled()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getNoiseTexturePath()).isNull();
}

@Test
void testParseBooleanTrue() {
// Given
String content = "oldLighting=true\nsun=true\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then
assertThat(props.getOldLighting()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getSun()).isEqualTo(OptionalBoolean.TRUE);
}

@Test
void testParseBooleanFalse() {
// Given
String content = "oldLighting=false\nmoon=false\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then
assertThat(props.getOldLighting()).isEqualTo(OptionalBoolean.FALSE);
assertThat(props.getMoon()).isEqualTo(OptionalBoolean.FALSE);
}

@Test
void testParseBoolean1AsTrue() {
// Given - IRIS accepts "1" as true
String content = "stars=1\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then
assertThat(props.getStars()).isEqualTo(OptionalBoolean.TRUE);
}

@Test
void testParseBoolean0AsFalse() {
// Given - IRIS accepts "0" as false
String content = "weather=0\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then
assertThat(props.getWeather()).isEqualTo(OptionalBoolean.FALSE);
}

@Test
void testParseNoiseTexturePath() {
// Given
String content = "texture.noise=/textures/noise.png\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then
assertThat(props.getNoiseTexturePath()).isEqualTo("/textures/noise.png");
}

@Test
void testParseMultipleProperties() {
// Given
String content = "oldLighting=true\nsun=false\nmoon=false\nweather=true\ntexture.noise=/custom/noise.png\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then
assertThat(props.getOldLighting()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getSun()).isEqualTo(OptionalBoolean.FALSE);
assertThat(props.getMoon()).isEqualTo(OptionalBoolean.FALSE);
assertThat(props.getWeather()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getNoiseTexturePath()).isEqualTo("/custom/noise.png");
}

@Test
void testParseShadowProperties() {
// Given
String content = "shadow.enabled=true\nshadowTerrain=true\nshadowTranslucent=false\nshadowEntities=true\nshadowPlayer=false\nshadowBlockEntities=true\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then
assertThat(props.getShadowEnabled()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getShadowTerrain()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getShadowTranslucent()).isEqualTo(OptionalBoolean.FALSE);
assertThat(props.getShadowEntities()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getShadowPlayer()).isEqualTo(OptionalBoolean.FALSE);
assertThat(props.getShadowBlockEntities()).isEqualTo(OptionalBoolean.TRUE);
}

@Test
void testUnparsedPropertiesRemainDefault() {
// Given - Only set one property
String content = "oldLighting=true\n";

// When
ShaderProperties props = new ShaderProperties(content);

// Then - Others should remain DEFAULT
assertThat(props.getOldLighting()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getSun()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getMoon()).isEqualTo(OptionalBoolean.DEFAULT);
}

@Test
void testLoadFromPackSource() throws IOException {
// Given
ShaderPackSource mockSource = new ShaderPackSource() {
@Override
public String getName() {
return "test_pack";
}

@Override
public java.util.Optional<String> readFile(String relativePath) {
return java.util.Optional.of("oldLighting=true\nsun=false\n");
}

@Override
public boolean fileExists(String relativePath) {
return true;
}

@Override
public java.util.List<String> listFiles(String directory) {
return java.util.List.of();
}
};

// When
ShaderProperties props = ShaderProperties.load(mockSource);

// Then
assertThat(props.getOldLighting()).isEqualTo(OptionalBoolean.TRUE);
assertThat(props.getSun()).isEqualTo(OptionalBoolean.FALSE);
}

@Test
void testLoadFromPackSourceWithMissingFile() throws IOException {
// Given
ShaderPackSource mockSource = new ShaderPackSource() {
@Override
public String getName() {
return "minimal_pack";
}

@Override
public java.util.Optional<String> readFile(String relativePath) {
return java.util.Optional.empty();
}

@Override
public boolean fileExists(String relativePath) {
return false;
}

@Override
public java.util.List<String> listFiles(String directory) {
return java.util.List.of();
}
};

// When
ShaderProperties props = ShaderProperties.load(mockSource);

// Then - Should return empty properties (all defaults)
assertThat(props.getOldLighting()).isEqualTo(OptionalBoolean.DEFAULT);
assertThat(props.getSun()).isEqualTo(OptionalBoolean.DEFAULT);
}
}
