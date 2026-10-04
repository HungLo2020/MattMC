//! Native row-domain regression: prepared gradient, world sky and view ray.
//! These procedural inputs supplement the real-pack paired gameplay capture.
use super::*;
use crate::render::shaderpack::lowering::{lower_fullscreen_source_pair_with_raster_primitive, FullscreenSourceRasterPrimitive};
use crate::render::shaderpack::uniforms::source::TerrainSourceUniformFrame;
use crate::render::shaderpack::programs::TerrainSourceTextureTransforms;
use crate::render::vulkanic::commands::{BufferImageCopyRegion, TextureOrigin3d, ClearColor};

#[test]
fn fullscreen_coordinate_domains_preserve_prepared_sky_and_upward_view_rays_on_native_device() {
    let source = ShaderPackSource::new("coordinate-pixels", 9, vec![
        ShaderSourceFile::new("lib/pipelineSettings.glsl", SETTINGS),
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, BINDINGS),
        ShaderSourceFile::new("world0/prepare.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/prepare.fsh", "#version 130\nuniform float viewHeight;\n/* DRAWBUFFERS:7 */\nvoid main() { gl_FragData[0]=vec4(gl_FragCoord.y/viewHeight,0.0,0.0,1.0); }\n"),
        // A camera projection of the owned XZ disc covers this tiny viewport.
        ShaderSourceFile::new("world0/gbuffers_skybasic.vsh", "#version 130\nvoid main() { gl_Position=vec4(gl_Vertex.x/256.0,gl_Vertex.z/256.0,0.0,1.0); }\n"),
        ShaderSourceFile::new("world0/gbuffers_skybasic.fsh", "#version 130\nuniform float viewHeight;\nuniform sampler2D colortex7;\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0]=texture2DLod(colortex7,gl_FragCoord.xy/vec2(16.0,viewHeight),0.0); }\n"),
        ShaderSourceFile::new("world0/deferred.vsh", "#version 130\nvarying vec2 texcoord;\nvoid main() { texcoord=gl_MultiTexCoord0.xy; gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/deferred.fsh", "#version 130\nvarying vec2 texcoord;\nuniform mat4 gbufferProjectionInverse;\n/* DRAWBUFFERS:1 */\nvoid main() { vec4 ray=gbufferProjectionInverse * (vec4(texcoord,1.0,1.0)*2.0-1.0); gl_FragData[0]=vec4(ray.y*0.5+0.5,0.0,0.0,1.0); }\n"),
    ]).unwrap();
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("fullscreen-coordinate-pixels").unwrap();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let extent = targets.identity.extent;
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let identity = [1.0,0.0,0.0,0.0, 0.0,1.0,0.0,0.0, 0.0,0.0,1.0,0.0, 0.0,0.0,0.0,1.0];
    let uniforms = TerrainSourceUniformFrame {
        viewport_height: Some(16.0), view_matrix: Some(identity), projection_matrix: Some(identity),
        projection_matrix_inverse: Some(identity), sky_color: Some([0.5,0.6,0.8]),
        ..Default::default()
    };
    let barrier = |resource,before,after| CommandOp::Barrier(ResourceBarrier {
        resource,subresources:None,before,after,src_queue:QueueClass::Graphics,dst_queue:QueueClass::Graphics,
    });
    let mut operations = Vec::new();
    let mut plans = Vec::new();
    let mut readbacks = Vec::new();
    for (name,raster) in [
        ("prepare",FullscreenSourceRasterPrimitive::FullscreenTriangle),
        ("gbuffers_skybasic",FullscreenSourceRasterPrimitive::VanillaSkyDisc),
        ("deferred",FullscreenSourceRasterPrimitive::FullscreenTriangle),
    ] {
        let path = format!("world0/{name}.fsh");
        let stages = TerrainSourceStages {
            vertex: TerrainSourceStage { path:format!("world0/{name}.vsh"),defines:Default::default() },
            fragment: TerrainSourceStage { path:path.clone(),defines:Default::default() },
        };
        let artifacts = preprocess_source_stage_pair(&source,&stages).unwrap();
        let lowered = lower_fullscreen_source_pair_with_raster_primitive(&artifacts.vertex,&artifacts.fragment,&bindings,raster).unwrap();
        let opaque = lowered.opaque_resource_contract().bind_semantic_roles(&bindings).unwrap();
        let program = prepare_lowered_fullscreen_source_program(source.name(),source.generation(),&path,&lowered,&opaque).unwrap();
        let plan = FullscreenSourceExecutionPlan::stage(&mut gal,&program,&manifest,&targets,
            std::iter::once(empty_source_resource_snapshot(&source)),extent).unwrap();
        plan.append_draw(&program,FullscreenSourcePassFrame {
            texture_transforms:program.pack_texture_transforms(&TerrainSourceTextureTransforms::canonical_minecraft_terrain()).unwrap(),
            scalar_uniforms:program.pack_scalar_uniforms(&uniforms).unwrap(),
            texture_transform_before:TextureUsageState::Undefined,
            scalar_uniform_before:program.execution_interface.scalar_uniforms.map(|_|TextureUsageState::Undefined),
            clear_values:ShaderPackColorClearValues { fog_color:ClearColor { r:0.0,g:0.0,b:0.0,a:1.0 } },
            color_attachment_before:vec![TextureUsageState::Undefined],clear_targets_this_pass:None,
        },&mut operations).unwrap();
        let texture = plan.outputs()[0].texture;
        let readback = gal.create_buffer(BufferDesc { label:format!("coordinate-{name}.readback"),size:16*16*4,
            memory:MemoryDomain::Readback,usages:vec![BufferUsage::TransferDst,BufferUsage::HostRead] }).unwrap();
        operations.extend([
            barrier(texture,TextureUsageState::ShaderRead,TextureUsageState::TransferSrc),
            CommandOp::CopyTextureToBuffer(BufferImageCopyRegion { buffer:readback,buffer_offset:0,bytes_per_row:16*4,rows_per_image:16,
                texture,texture_mip:0,texture_layer:0,texture_origin:TextureOrigin3d { x:0,y:0,z:0 },extent }),
            barrier(texture,TextureUsageState::TransferSrc,TextureUsageState::ShaderRead),
            barrier(readback,TextureUsageState::TransferDst,TextureUsageState::ShaderRead),
            CommandOp::HostReadBuffer { buffer:readback,offset:0,size:16*16*4 },
        ]);
        plans.push(plan);readbacks.push((name,readback));
    }
    let token = gal.submit(SubmissionBatch { label:"coordinate-domains".into(),command_lists:vec![CommandList::from(
        CommandListDesc { label:"coordinate-domains".into(),operations })] }).unwrap();
    gal.retire_through_for_test(token.submission).unwrap();
    let reads = gal.completed_host_reads();
    for (name,readback) in &readbacks {
        let pixels = &reads.iter().find(|read|read.buffer==*readback).unwrap().bytes;
        for (index,pixel) in pixels.chunks_exact(4).enumerate() {
            let y = (index/16) as f32+0.5;
            let expected = (1.0-y/16.0)*255.0;
            assert!((pixel[0] as f32-expected).abs()<=1.0,"{name} row {}: {pixel:?}, expected red {expected}",index/16);
            assert_eq!(255,pixel[3],"{name}: uncovered pixel {index}");
        }
    }
    for (_,readback) in readbacks { gal.destroy(readback).unwrap(); }
    for plan in plans.into_iter().rev() { plan.destroy(&mut gal); }
    cache.destroy(&mut gal);
    assert_eq!(gal.metrics().resource_creates,gal.metrics().resource_destroys);
}

#[test]
fn fullscreen_authored_texels_sample_source_top_rows_and_keep_native_fragment_texels() {
    let source = ShaderPackSource::new("coordinate-pixels", 9, vec![
        ShaderSourceFile::new("lib/pipelineSettings.glsl", SETTINGS),
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, BINDINGS),
        ShaderSourceFile::new("world0/prepare.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/prepare.fsh", "#version 130\nuniform float viewHeight;\n/* DRAWBUFFERS:7 */\nvoid main() { gl_FragData[0]=vec4(gl_FragCoord.y/viewHeight,0.0,0.0,1.0); }\n"),
        ShaderSourceFile::new("world0/deferred.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/deferred.fsh", "#version 130\nuniform sampler2D colortex7;\nuniform float viewWidth;\nuniform float viewHeight;\n/* DRAWBUFFERS:0 */\nvec2 Reprojection(vec4 p) { vec4 previousPosition=p; return previousPosition.xy / previousPosition.w * 0.5 + 0.5; }\nvoid main() { vec2 view = vec2(viewWidth, viewHeight); ivec2 texelCoord = ivec2(gl_FragCoord.xy); float count = 0.0; for (float i=0.1; i<1.0; i+=0.2) { count += float(texelFetch(colortex7, ivec2(view.x*i,view.y*0.9),0).r > 0.8); } vec2 historyUV=Reprojection(vec4(0.0,0.8,0.0,1.0)); float history=texelFetch(colortex7,ivec2(historyUV*view),0).r; gl_FragData[0]=vec4(count/5.0,texelFetch(colortex7,texelCoord,0).r,history,1.0); }\n"),
    ]).unwrap();
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("fullscreen-coordinate-pixels").unwrap();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, false);
    let extent = targets.identity.extent;
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let identity = [1.0,0.0,0.0,0.0, 0.0,1.0,0.0,0.0, 0.0,0.0,1.0,0.0, 0.0,0.0,0.0,1.0];
    let uniforms = TerrainSourceUniformFrame {
        viewport_width: Some(16.0), viewport_height: Some(16.0), view_matrix: Some(identity), projection_matrix: Some(identity),
        projection_matrix_inverse: Some(identity), sky_color: Some([0.5,0.6,0.8]),
        ..Default::default()
    };
    let barrier = |resource,before,after| CommandOp::Barrier(ResourceBarrier {
        resource,subresources:None,before,after,src_queue:QueueClass::Graphics,dst_queue:QueueClass::Graphics,
    });
    let mut operations = Vec::new();
    let mut plans = Vec::new();
    let mut readbacks = Vec::new();
    for (name,raster) in [
        ("prepare",FullscreenSourceRasterPrimitive::FullscreenTriangle),
        ("deferred",FullscreenSourceRasterPrimitive::FullscreenTriangle),
    ] {
        let path = format!("world0/{name}.fsh");
        let stages = TerrainSourceStages {
            vertex: TerrainSourceStage { path:format!("world0/{name}.vsh"),defines:Default::default() },
            fragment: TerrainSourceStage { path:path.clone(),defines:Default::default() },
        };
        let artifacts = preprocess_source_stage_pair(&source,&stages).unwrap();
        let lowered = lower_fullscreen_source_pair_with_raster_primitive(&artifacts.vertex,&artifacts.fragment,&bindings,raster).unwrap();
        let opaque = lowered.opaque_resource_contract().bind_semantic_roles(&bindings).unwrap();
        let program = prepare_lowered_fullscreen_source_program(source.name(),source.generation(),&path,&lowered,&opaque).unwrap();
        let plan = FullscreenSourceExecutionPlan::stage(&mut gal,&program,&manifest,&targets,
            std::iter::once(empty_source_resource_snapshot(&source)),extent).unwrap();
        plan.append_draw(&program,FullscreenSourcePassFrame {
            texture_transforms:program.pack_texture_transforms(&TerrainSourceTextureTransforms::canonical_minecraft_terrain()).unwrap(),
            scalar_uniforms:program.pack_scalar_uniforms(&uniforms).unwrap(),
            texture_transform_before:TextureUsageState::Undefined,
            scalar_uniform_before:program.execution_interface.scalar_uniforms.map(|_|TextureUsageState::Undefined),
            clear_values:ShaderPackColorClearValues { fog_color:ClearColor { r:0.0,g:0.0,b:0.0,a:1.0 } },
            color_attachment_before:vec![TextureUsageState::Undefined],clear_targets_this_pass:None,
        },&mut operations).unwrap();
        let texture = plan.outputs()[0].texture;
        let readback = gal.create_buffer(BufferDesc { label:format!("coordinate-{name}.readback"),size:16*16*4,
            memory:MemoryDomain::Readback,usages:vec![BufferUsage::TransferDst,BufferUsage::HostRead] }).unwrap();
        operations.extend([
            barrier(texture,TextureUsageState::ShaderRead,TextureUsageState::TransferSrc),
            CommandOp::CopyTextureToBuffer(BufferImageCopyRegion { buffer:readback,buffer_offset:0,bytes_per_row:16*4,rows_per_image:16,
                texture,texture_mip:0,texture_layer:0,texture_origin:TextureOrigin3d { x:0,y:0,z:0 },extent }),
            barrier(texture,TextureUsageState::TransferSrc,TextureUsageState::ShaderRead),
            barrier(readback,TextureUsageState::TransferDst,TextureUsageState::ShaderRead),
            CommandOp::HostReadBuffer { buffer:readback,offset:0,size:16*16*4 },
        ]);
        plans.push(plan);readbacks.push((name,readback));
    }
    let token = gal.submit(SubmissionBatch { label:"coordinate-domains".into(),command_lists:vec![CommandList::from(
        CommandListDesc { label:"coordinate-domains".into(),operations })] }).unwrap();
    gal.retire_through_for_test(token.submission).unwrap();
    let reads = gal.completed_host_reads();
    for (name,readback) in &readbacks {
        let pixels = &reads.iter().find(|read|read.buffer==*readback).unwrap().bytes;
        for (index,pixel) in pixels.chunks_exact(4).enumerate() {
            let y = (index/16) as f32+0.5;
            let expected = (1.0-y/16.0)*255.0;
            let channel = if *name == "deferred" { 1 } else { 0 };
            assert!((pixel[channel] as f32-expected).abs()<=1.0,"{name} row {}: {pixel:?}, expected gradient {expected}",index/16);
            if *name == "deferred" {
                assert_eq!(255, pixel[0], "authored sky-check must sample the top row, not terrain at the bottom");
                assert!((pixel[2] as i32 - 231).abs() <= 1, "reprojected history already addresses native top rows: {pixel:?}");
            }
            assert_eq!(255,pixel[3],"{name}: uncovered pixel {index}");
        }
    }
    for (_,readback) in readbacks { gal.destroy(readback).unwrap(); }
    for plan in plans.into_iter().rev() { plan.destroy(&mut gal); }
    cache.destroy(&mut gal);
    assert_eq!(gal.metrics().resource_creates,gal.metrics().resource_destroys);
}
