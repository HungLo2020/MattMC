//! World sky sampling and composite reconstruction use different UV domains.
use super::*;

fn lower_coordinates(vertex: &str, fragment: &str, raster: FullscreenSourceRasterPrimitive)
    -> LoweredFullscreenSourcePair
{
    let pack = ShaderPackSource::new("coordinate-domains", 1, vec![
        ShaderSourceFile::new("world0/prepare.vsh", vertex),
        ShaderSourceFile::new("world0/prepare.fsh", fragment),
        ShaderSourceFile::new(crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
            "colortex1=shader_pack_color:previous_depth\ncolortex7=shader_pack_color:temporal_reflection\ngaux4=shader_pack_color:temporal_reflection\nnoisetex=noise\n"),
    ]).unwrap();
    let vertex = preprocess_artifact(PreprocessInput { source: &pack, entry: "world0/prepare.vsh", defines: &[] }).unwrap();
    let fragment = preprocess_artifact(PreprocessInput { source: &pack, entry: "world0/prepare.fsh", defines: &[] }).unwrap();
    lower_fullscreen_source_pair_with_raster_primitive(&vertex, &fragment,
        &TerrainSourceResourceBindings::from_source(&pack).unwrap(), raster).unwrap()
}

#[test]
fn fullscreen_coordinate_domains_sky_samples_source_targets_in_native_rows() {
    for raster in [FullscreenSourceRasterPrimitive::VanillaSkyDisc,
                   FullscreenSourceRasterPrimitive::VanillaCelestialQuad] {
        let pair = lower_coordinates(
            "#version 130\nvoid main() { gl_Position=ftransform(); }\n",
            "#version 130\nuniform float viewHeight;\nuniform sampler2D gaux4;\nuniform sampler2D noisetex;\n/* DRAWBUFFERS:1 */\nvoid main() { vec2 p=gl_FragCoord.xy/vec2(16.0,viewHeight); gl_FragData[0]=texture2DLod(gaux4,p,0.0)+texture2D(noisetex,p); }\n",
            raster);
        let fragment = pair.fragment().source();
        assert!(fragment.contains("vulkanic_source_world_target_uv"), "world sky target sampling must convert source-screen rows");
        assert!(fragment.contains("texture(noisetex,p)"), "copied pack noise keeps its authored UV domain");
    }
}

#[test]
fn fullscreen_coordinate_domains_inline_inverse_projection_uses_declared_uv_names() {
    for name in ["texcoord", "texCoord", "imageUV"] {
        // No gl_FragCoord: projection conversion is independently required.
        let pair = lower_coordinates(
            &format!("#version 130\nvarying vec2 {name};\nvoid main() {{ {name}=gl_MultiTexCoord0.xy; gl_Position=ftransform(); }}\n"),
            &format!("#version 130\nvarying vec2 {name};\nuniform mat4 gbufferProjectionInverse;\nuniform sampler2D colortex7;\n/* DRAWBUFFERS:1 */\nvoid main() {{ vec4 ray=gbufferProjectionInverse /* camera */ * ( vec4 ( {name}, 1.0, 1.0) * 2.0 - 1.0); gl_FragData[0]=texture2D(colortex7,{name})+vec4(ray.xy,0.0,0.0); }}\n"),
            FullscreenSourceRasterPrimitive::FullscreenTriangle);
        let fragment = pair.fragment().source();
        assert!(fragment.contains(&format!("vulkanic_source_fullscreen_screen_uv({name})")), "inverse projection missed declared UV {name}");
        assert!(fragment.contains(&format!("texture(colortex7,{name})")), "composite target sampling must remain in image rows");
        assert!(!fragment.contains("viewHeight"), "projection conversion must not invent a viewport uniform");
    }
}
