//! Absolute source texels and native per-fragment texels must remain distinct.
use super::*;

fn lower(fragment: &str, binding: &str, vertex_read: Option<&str>) -> LoweredFullscreenSourcePair {
    let vertex = format!("#version 130\nvarying vec2 imageUV;\n{}\nvoid main() {{ imageUV=gl_MultiTexCoord0.xy; gl_Position=ftransform(); }}\n", vertex_read.unwrap_or(""));
    let fragment = format!("#version 130\nvarying vec2 imageUV;\nuniform float viewWidth;\nuniform float viewHeight;\n/* DRAWBUFFERS:0 */\n{fragment}");
    let pack = ShaderPackSource::new(
        "authored-texels",
        1,
        vec![
            ShaderSourceFile::new("world0/composite.vsh", vertex),
            ShaderSourceFile::new("world0/composite.fsh", fragment),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                format!("colortex0=shader_pack_color:primary\n{binding}"),
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/composite.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/composite.fsh",
        defines: &[],
    })
    .unwrap();
    lower_fullscreen_source_pair(
        &vertex,
        &fragment,
        &TerrainSourceResourceBindings::from_source(&pack).unwrap(),
    )
    .unwrap()
}

#[test]
fn fullscreen_authored_texels_follow_view_aliases_comments_and_sampled_mip() {
    let pair = lower("uniform sampler2D sceneDepth;\nvoid main() { vec2 dimensions = vec2(viewWidth,viewHeight); ivec2 corner = ivec2(dimensions * vec2(0.2,0.9)); gl_FragData[0] = texelFetch /* address */ (sceneDepth, corner, 2); }", "sceneDepth=main_depth\n",None);
    let source = pair.fragment().source();
    assert!(source.contains(
        "vulkanic_source_fullscreen_fetch_source_texel /* address */ (sceneDepth, corner, 2)"
    ));
    assert!(source.contains("textureSize(source_sampler, level)"));
    assert!(source.contains("extent.y - 1 - coordinate.y"));
}

#[test]
fn fullscreen_authored_texels_literal_vertex_and_fragment_addresses_convert_without_extra_uniforms()
{
    let pair=lower("uniform sampler2D colortex0;\nvoid main() { gl_FragData[0]=texelFetch(colortex0,ivec2(3,4),0); }", "",Some("uniform sampler2D colortex0; vec4 history=texelFetch(colortex0,ivec2(2,5),0);"));
    for source in [pair.vertex().source(), pair.fragment().source()] {
        assert!(source.contains("vulkanic_source_fullscreen_fetch_source_texel(colortex0,ivec2("));
        assert!(!source.contains("viewHeight"));
    }
}

#[test]
fn fullscreen_authored_texels_keep_native_varying_and_fragment_aliases() {
    let pair=lower("uniform sampler2D colortex0;\nvoid main() { ivec2 texelCoord = ivec2(gl_FragCoord.xy); ivec2 alias=texelCoord; ivec2 fromVarying=ivec2(imageUV*vec2(viewWidth,viewHeight)); gl_FragData[0]=texelFetch(colortex0,alias,0)+texelFetch(colortex0,fromVarying,0); }", "",None);
    let source = pair.fragment().source();
    assert!(source.contains("texelFetch(colortex0,alias,0)"));
    assert!(source.contains("texelFetch(colortex0,fromVarying,0)"));
    assert!(!source.contains("vulkanic_source_fullscreen_fetch_source_texel"));
}

#[test]
fn fullscreen_authored_texels_respect_pack_png_override_of_conventional_sampler_name() {
    let pair=lower("uniform sampler2D depthtex0;\nvoid main() { gl_FragData[0]=texelFetch(depthtex0,ivec2(viewWidth*0.5,viewHeight*0.9),0); }", "depthtex0=pack_texture:lib/noise.png\n",None);
    assert!(!pair
        .fragment()
        .source()
        .contains("vulkanic_source_fullscreen_fetch_source_texel"));
}

#[test]
fn fullscreen_authored_texels_real_pack_sky_check_uses_source_rows() {
    let pack =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test();
    let vertex =
        crate::render::shaderpack::source::preprocess::preprocess_artifact_with_runtime_options(
            &pack,
            "world0/composite.vsh",
            &[],
        )
        .unwrap();
    let fragment =
        crate::render::shaderpack::source::preprocess::preprocess_artifact_with_runtime_options(
            &pack,
            "world0/composite.fsh",
            &[],
        )
        .unwrap();
    let bindings =
        TerrainSourceResourceBindings::from_preprocessed_stage(&pack, &fragment).unwrap();
    let pair = lower_fullscreen_source_pair(&vertex, &fragment, &bindings).unwrap();
    let source = pair.fragment().source();
    assert!(source.contains("vulkanic_source_fullscreen_fetch_source_texel(depthtex0, ivec2(view.x * i, view.y * 0.9), 0)"), "the real sky-detection loop still samples native bottom rows");
    assert!(source.contains("texelFetch(depthtex0, texelCoord, 0)"));
    assert!(source.contains("GetVolumetricLight"));
}

#[test]
fn fullscreen_authored_texels_member_names_do_not_acquire_local_variable_origins() {
    let pair=lower("uniform sampler2D colortex0;\nvoid main() { float y=imageUV.y; vec2 view=vec2(viewWidth,viewHeight); gl_FragData[0]=texelFetch(colortex0,ivec2(view.x*0.5,view . y*0.9),0)+vec4(y); }", "",None);
    assert!(pair.fragment().source().contains(
        "vulkanic_source_fullscreen_fetch_source_texel(colortex0,ivec2(view.x*0.5,view . y*0.9),0)"
    ));
}

#[test]
fn fullscreen_authored_texels_reassignment_mixed_domains_keep_native_coordinates() {
    let pair=lower("uniform sampler2D colortex0;\nvoid main() { ivec2 position=ivec2(viewWidth*0.5,viewHeight*0.9); position=ivec2(imageUV*vec2(viewWidth,viewHeight)); gl_FragData[0]=texelFetch(colortex0,position,0); }", "",None);
    assert!(!pair
        .fragment()
        .source()
        .contains("vulkanic_source_fullscreen_fetch_source_texel"));
}

#[test]
fn fullscreen_authored_texels_reprojected_history_stays_in_native_image_rows() {
    let pair=lower("uniform sampler2D colortex0;\nvec2 Reprojection(vec4 p) { vec4 previousPosition=p; return previousPosition.xy / previousPosition.w * 0.5 + 0.5; }\nvoid main() { ivec2 texelCoord = ivec2(gl_FragCoord.xy); vec2 previousUV=Reprojection(vec4(0.0,0.0,0.5,1.0)); ivec2 historyPixel=ivec2(previousUV*vec2(viewWidth,viewHeight)); gl_FragData[0]=texelFetch(colortex0,historyPixel,0); }", "",None);
    let source = pair.fragment().source();
    assert!(source.contains("return vulkanic_source_fullscreen_screen_uv(previousPosition.xy / previousPosition.w * 0.5 + 0.5);"));
    assert!(
        source.contains("texelFetch(colortex0,historyPixel,0)"),
        "reprojection already converted its output into native image rows"
    );
}
