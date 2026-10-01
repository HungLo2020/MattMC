use crate::render::shaderpack::contracts::distant_horizons::*;
use crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test;
use crate::render::shaderpack::source::{ShaderSourceFile, RUNTIME_OPTIONS_PATH};

fn source_with_runtime_options(distant_horizons: bool) -> ShaderPackSource {
    let source = complete_bundled_pack_source_for_test();
    ShaderPackSource::new(
        "ComplementaryHungLoIfied-dh-water-runtime-options",
        92,
        source
            .files()
            .into_iter()
            .chain(std::iter::once(ShaderSourceFile::new(
                RUNTIME_OPTIONS_PATH,
                format!(
                    "{}SHADOW_QUALITY=-1\nFXAA_DEFINE=-1\nCOLORED_LIGHTING=0\nENTITY_SHADOWS_DEFINE=-1\nPLAYER_SHADOW=-1\nRAIN_PUDDLES=0\n",
                    if distant_horizons {
                        "DISTANT_HORIZONS=1\n"
                    } else {
                        ""
                    },
                ),
            )))
            .collect(),
    )
    .unwrap()
}

fn source_with_distant_horizons_enabled() -> ShaderPackSource {
    source_with_runtime_options(true)
}

#[test]
fn bundled_complementary_dh_contract_keeps_shared_color_and_distant_depth_explicit() {
    let source = complete_bundled_pack_source_for_test();
    let contract =
        derive_distant_horizons_opaque_contract(&source, TerrainProgramScope::Overworld)
            .unwrap();
    assert_eq!("world0/dh_terrain.fsh", contract.program_path);
    assert_eq!("world0/dh_terrain.vsh", contract.source_stages.vertex.path);
    assert!(contract
        .inputs
        .contains(&DistantHorizonsPassInput::Lightmap));
    assert!(contract
        .inputs
        .contains(&DistantHorizonsPassInput::MaterialCategory));
    assert!(contract
        .outputs
        .contains(&DistantHorizonsPassOutput::ShaderPackPrimaryColor));
    assert!(contract
        .outputs
        .contains(&DistantHorizonsPassOutput::DistantDepth));
    assert!(contract.requires_distant_depth_consumers());
    assert_eq!(
        "world0/deferred1.fsh",
        contract.post_terrain_stages[0].stage_path
    );
    assert_eq!(
        "world0/final.fsh",
        contract.post_terrain_stages.last().unwrap().stage_path
    );
    assert!(contract.distant_depth_consumers.iter().any(|consumer| {
        consumer.stage_path == "world0/deferred1.fsh" && consumer.reads_opaque_depth
    }));
    assert!(contract.distant_depth_consumers.iter().any(|consumer| {
        consumer.stage_path == "world0/composite.fsh"
            && consumer.reads_opaque_depth
            && consumer.reads_depth_before_translucency
    }));
    assert!(contract
        .distant_depth_consumers
        .iter()
        .all(|consumer| consumer.stage_path.starts_with("world0/")));
    assert!(contract
        .distant_depth_consumers
        .iter()
        .all(|consumer| !consumer.stage_path.contains("gbuffers_entities")));
}

#[test]
fn dh_contract_never_falls_back_to_ordinary_terrain_source() {
    let source = complete_bundled_pack_source_for_test();
    let missing = ShaderPackSource::new(
        "ComplementaryHungLoIfied-no-dh",
        8,
        source
            .files()
            .into_iter()
            .filter(|file| !file.path.contains("dh_terrain"))
            .collect(),
    )
    .unwrap();
    let error =
        derive_distant_horizons_opaque_contract(&missing, TerrainProgramScope::Overworld)
            .unwrap_err();
    assert!(error
        .to_string()
        .contains("missing Distant Horizons terrain fragment source"));
}

#[test]
fn dh_contract_discovers_depth_consumers_from_sampling_not_uniform_declarations() {
    let source = complete_bundled_pack_source_for_test();
    let without_sampling = ShaderPackSource::new(
        "ComplementaryHungLoIfied-no-dh-depth-consumers",
        8,
        source
            .files()
            .into_iter()
            .map(|mut file| {
                if !file.path.contains("dh_terrain") && !file.path.contains("dh_water") {
                    file.contents = file.contents.replace("dhDepthTex", "notDhDepthTex");
                }
                file
            })
            .collect(),
    )
    .unwrap();
    let contract = derive_distant_horizons_opaque_contract(
        &without_sampling,
        TerrainProgramScope::Overworld,
    )
    .unwrap();
    assert!(!contract.requires_distant_depth_consumers());
    assert!(contract.distant_depth_consumers.is_empty());
    assert_eq!(
        1,
        contract
            .pass_graph(ProgramIdentity::new("pack:world0/dh_terrain"))
            .unwrap()
            .passes()
            .len()
    );
}

#[test]
fn dh_contract_writes_shared_color_and_retains_distant_depth() {
    let source = complete_bundled_pack_source_for_test();
    let contract =
        derive_distant_horizons_opaque_contract(&source, TerrainProgramScope::Overworld)
            .unwrap();
    assert_eq!(
        vec![
            AttachmentRole::ShaderPackPrimaryColor,
            AttachmentRole::DistantDepth
        ],
        contract.output_roles().unwrap()
    );
    let graph = contract
        .pass_graph(ProgramIdentity::new("pack:world0/dh_terrain"))
        .unwrap();
    assert_eq!(1, graph.passes().len());
    assert_eq!(
        "vulkanic:pass/distant_horizons_opaque",
        graph.passes()[0].identity.as_str()
    );
    assert_eq!(
        vec![AttachmentRole::ShaderPackPrimaryColor],
        graph.passes()[0].colors
    );
    assert_eq!(Some(AttachmentRole::DistantDepth), graph.passes()[0].depth);
}

#[test]
fn dh_water_contract_keeps_dh_translucency_separate_from_near_terrain() {
    let source = source_with_distant_horizons_enabled();
    let contract =
        derive_distant_horizons_translucent_contract(&source, TerrainProgramScope::Overworld)
            .unwrap();

    assert_eq!(DistantHorizonsPassKind::Translucent, contract.pass_kind);
    assert_eq!("world0/dh_water.fsh", contract.program_path);
    assert_eq!("world0/dh_water.vsh", contract.source_stages.vertex.path);
    assert_eq!(
        Some(TerrainTranslucentBlend::SourceAlphaOver),
        contract.translucent_blend
    );
    assert!(contract
        .inputs
        .contains(&DistantHorizonsPassInput::MainDepthBeforeTranslucency));
    assert_eq!(
        vec![AttachmentRole::ShaderPackPrimaryColor],
        contract.output_roles().unwrap()
    );
    assert!(!contract
        .outputs
        .contains(&DistantHorizonsPassOutput::DistantDepth));
    let graph = contract
        .pass_graph(ProgramIdentity::new("pack:world0/dh_water"))
        .unwrap();
    assert_eq!(1, graph.passes().len());
    assert_eq!(
        "vulkanic:pass/distant_horizons_translucent",
        graph.passes()[0].identity.as_str()
    );
    assert_eq!(
        vec![AttachmentRole::ShaderPackPrimaryColor],
        graph.passes()[0].colors
    );
    assert_eq!(Some(AttachmentRole::DistantDepth), graph.passes()[0].depth);
}

#[test]
fn dh_water_contract_requires_the_selected_source_blend_property() {
    let source = source_with_runtime_options(false);
    let error =
        derive_distant_horizons_translucent_contract(&source, TerrainProgramScope::Overworld)
            .unwrap_err();
    assert!(error.to_string().contains("blend.dh_water"));
}

#[test]
fn dh_water_contract_never_falls_back_to_dh_terrain() {
    let source = source_with_distant_horizons_enabled();
    let missing = ShaderPackSource::new(
        "ComplementaryHungLoIfied-no-dh-water",
        93,
        source
            .files()
            .into_iter()
            .filter(|file| !file.path.contains("dh_water"))
            .collect(),
    )
    .unwrap();
    let error =
        derive_distant_horizons_translucent_contract(&missing, TerrainProgramScope::Overworld)
            .unwrap_err();
    assert!(error
        .to_string()
        .contains("missing Distant Horizons translucent fragment source"));
}
