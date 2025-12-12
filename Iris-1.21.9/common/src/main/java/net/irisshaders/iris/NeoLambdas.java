package net.irisshaders.iris;

public class NeoLambdas {
    // MattMC uses official Mojang mappings - target the named wrapper methods we add to LevelRenderer
    public static final String NEO_RENDER_SKY = "iris$renderSkyPassBody";
    public static final String NEO_RENDER_MAIN_PASS = "iris$renderMainPassBody";
    public static final String NEO_RENDER_WEATHER = "iris$renderWeatherPassBody";
    public static final String NEO_RENDER_CLOUDS = "iris$renderCloudsPassBody";
	public static final String NEO_PARTICLE = "iris$renderParticlesPassBody";
	public static final String NEO_WEATHER_TYPE = "iris$createWeatherBody";
	public static final String NEO_RENDER_WORLD_BORDER = "iris$renderWorldBorderBody";
	public static final String NEO_BEGIN_DEBUG_RENDER = "iris$beginDebugRender";
	public static final String NEO_END_DEBUG_RENDER = "iris$endDebugRender";
	public static final String NEO_BEGIN_TRANSLUCENTS = "iris$beginTranslucents";
}
