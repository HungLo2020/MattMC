//! Bounded observation of layered equipment commands in a selected submission.
//! No GPU readback, renderer decisions, or capability admission.
use super::*;
use super::decal_capture::{floats, uploaded_range};
use serde_json::{json, Value};
const MAX_RECORDS: usize = 64;
fn invalid(s: &str) -> GalError { GalError::invalid_argument(s) }

pub(super) fn observe(gal: &VulkanicGal, frontend: &WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame, ops: &[CommandOp]) -> Value {
    observe_scope(gal, frontend, frame, ops, false)
}
pub(super) fn observe_wolf(gal: &VulkanicGal, frontend: &WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame, ops: &[CommandOp]) -> Value {
    observe_scope(gal, frontend, frame, ops, true)
}
fn observe_scope(gal: &VulkanicGal, frontend: &WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame, ops: &[CommandOp], wolf: bool) -> Value {
    let schema = if wolf { "wolf-submission-inputs-v1" } else { "equipment-submission-inputs-v1" };
    match observe_inner(gal, frontend, frame, ops, wolf) {
        Ok((semantics, draws, textures, model_textures)) => json!({"schema":schema,
            "complete":true,"gpu_readback":false,"capability_admitted":false,
            "world_view_matrix":frame.view_matrix,"first_person_view_matrix":frame.first_person.model_view_matrix,
            "semantic_instances":semantics,"draws":draws,"texture_sources":textures,
            "model_texture_sources":model_textures}),
        Err(e) => json!({"schema":schema, "complete":false,
            "gpu_readback":false,"capability_admitted":false,"reason":e.to_string()}),
    }
}
fn observe_inner(gal: &VulkanicGal, frontend: &WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame, ops: &[CommandOp], wolf: bool) -> GalResult<(Vec<Value>,Vec<Value>,Vec<Value>,Vec<Value>)> {
    let mut semantics = Vec::new();
    for (context, instances) in [("world", &frame.mesh_instances),
        ("first-person", &frame.first_person_mesh_instances)] {
        for (index, instance) in instances.iter().enumerate() {
            let projection = mesh_view_layering(instance);
            let identity = frontend.mesh_assets.get(&instance.mesh_key).map(|a| a.entity_identity.as_str());
            if projection.is_none() && !(wolf && identity == Some("minecraft:wolf")) { continue; }
            if semantics.len() == MAX_RECORDS { return Err(invalid("equipment semantic capture limit")); }
            let foil = if let Some(f) = instance.item_foil {
                f.validate()?;
                json!({"kind":format!("{:?}",f.kind),"clock_millis":f.clock_millis,
                    "speed":f.speed,"strength":f.strength})
            } else { Value::Null };
            semantics.push(json!({"context":context,"instance_index":index,
                "mesh_key":instance.mesh_key,"mesh_generation":instance.mesh_generation,
                "color_argb":instance.color_argb,
                "section_index":instance.mesh_section_index,"flags":instance.flags,
                "model_submission_order":instance.model_submission_order,
                "projection":projection.map(|p|format!("{p:?}")),"entity_identity":identity,"model_pose":instance.transform,"foil":foil}));
        }
    }
    let mut draws = Vec::new();
    let mut textures = BTreeMap::new();
    let mut model_textures = BTreeMap::new();
    let mut texture_bytes = 0usize;
    let mut pipeline = Handle::NULL;
    let mut binding = None;
    let mut index_binding = None;
    for (op_index, op) in ops.iter().enumerate() {
        match op {
            CommandOp::BeginPass { .. } | CommandOp::EndPass => { pipeline=Handle::NULL; binding=None; index_binding=None; }
            CommandOp::SetIndexBuffer {buffer,offset,index_type} => index_binding=Some((*buffer,*offset,*index_type)),
            CommandOp::BindGraphicsPipeline(h) => pipeline=*h,
            CommandOp::BindResourceSet {pipeline_layout,set_index:0,set,dynamic_offsets} =>
                binding=Some((*pipeline_layout,*set,dynamic_offsets)),
            CommandOp::DrawIndexed {indices,instances} => {
                let Some((layout,set,offsets)) = binding else { continue; };
                let Some((key,resource)) = frontend.mesh_resources.iter().find(|(_,r)|r.resource_set==set)
                    else { continue; };
                let projection=key.view_layering;
                let identity=frontend.mesh_assets.get(&key.mesh_key).map(|a|a.entity_identity.as_str());
                if projection.is_none() && !(wolf && identity==Some("minecraft:wolf")) { continue; }
                if draws.len()==MAX_RECORDS || *instances==0 || *instances as usize>MAX_RECORDS {
                    return Err(invalid("equipment draw capture limit"));
                }
                if resource.pipeline!=pipeline || resource.pipeline_layout!=layout
                    || offsets.len()!=if key.standard_item_foil {3} else {2} {
                    return Err(invalid("equipment pipeline/binding mismatch"));
                }
                let pipeline_desc=gal.graphics_pipeline_descriptor_for_capture(pipeline)?;
                let (program, _)=frontend.mesh_pipeline_resources.iter().find(|(_,v)|v.pipeline==pipeline)
                    .ok_or_else(||invalid("equipment unowned pipeline"))?;
                let asset=frontend.mesh_assets.get(&key.mesh_key).ok_or_else(||invalid("equipment missing mesh"))?;
                if asset.mesh_generation!=key.mesh_generation || resource.geometry_key!=key.geometry_key()
                    || offsets[0]!=resource.vertex_offset {
                    return Err(invalid("equipment geometry incarnation mismatch"));
                }
                let descriptor=gal.resource_set_descriptor_for_capture(set)?;
                let mut geometry_uploads=None;
                {
                    let geometry=frontend.mesh_geometry_resources.get(&resource.geometry_key)
                        .ok_or_else(||invalid("equipment missing owned geometry allocation"))?;
                    let mut selected_range=None;
                    for instance in frame.mesh_instances.iter().chain(&frame.first_person_mesh_instances) {
                        if instance.mesh_key!=key.mesh_key || instance.mesh_generation!=key.mesh_generation {continue;}
                        let ranges=if instance.mesh_section_index==WORLD_MESH_SECTION_ALL {
                            compatible_mesh_section_ranges(instance,asset,key.color_format,key.raster_y_direction,key.g_buffer)?
                        } else {
                            let section=asset.sections.get(instance.mesh_section_index as usize)
                                .ok_or_else(||invalid("equipment missing explicit section"))?;
                            vec![MeshSectionRange {key:mesh_key_for_section(instance,section,instance.mesh_section_index,
                                instance.cull_policy,asset.mesh_generation,asset.vertex_bytes.len()/WORLD_MESH_GPU_VERTEX_BYTES,
                                key.color_format,key.raster_y_direction,key.g_buffer),
                                index_offset:u64::from(section.index_offset),index_count:section.index_count}]
                        };
                        for range in ranges {
                            if range.key==*key {selected_range=Some(range);}
                        }
                    }
                    let range=selected_range.ok_or_else(||invalid("equipment missing compatible geometry range"))?;
                    let expected_offset=resource.index_offset.checked_add(range.index_offset)
                        .ok_or_else(||invalid("equipment index offset overflow"))?;
                    let vertex=descriptor.bindings.iter().find(|b|b.binding==0)
                        .ok_or_else(||invalid("equipment missing geometry binding"))?;
                    if vertex.resource!=resource.vertex_buffer || vertex.kind!=ResourceBindingKind::StorageBuffer
                        || vertex.dynamic_offsets!=[0] || vertex.buffer_range!=Some(resource.vertex_range)
                        || geometry.vertex_buffer!=resource.vertex_buffer || geometry.vertex_offset!=resource.vertex_offset
                        || geometry.vertex_range!=resource.vertex_range || geometry.index_buffer!=resource.index_buffer
                        || geometry.index_offset!=resource.index_offset
                        || geometry.vertex_range!=asset.vertex_bytes.len() as u64
                        || geometry.index_range!=asset.index_bytes.len() as u64
                        || index_binding!=Some((resource.index_buffer,expected_offset,asset.index_type))
                        || *indices!=range.index_count {
                        return Err(invalid("equipment actual geometry binding differs from owned source incarnation"));
                    }
                    if !wolf || asset.entity_identity=="minecraft:wolf" && !key.standard_item_foil {
                        ensure_geometry_unwritten(gal,&ops[..op_index],resource.vertex_buffer,
                            resource.vertex_offset,asset.vertex_bytes.len())?;
                        ensure_geometry_unwritten(gal,&ops[..op_index],resource.index_buffer,
                            resource.index_offset,asset.index_bytes.len())?;
                        let (vertices,vertex_submission)=gal.buffer_upload_for_capture(resource.vertex_buffer,
                            resource.vertex_offset,asset.vertex_bytes.len())?;
                        let (indices,index_submission)=gal.buffer_upload_for_capture(resource.index_buffer,
                            resource.index_offset,asset.index_bytes.len())?;
                        if vertices!=asset.vertex_bytes || indices!=asset.index_bytes {
                            return Err(invalid("equipment accepted geometry uploads differ from source bytes"));
                        }
                        geometry_uploads=Some(json!({"contents_match_source":true,
                            "vertex_submission_id":vertex_submission.0,"index_submission_id":index_submission.0}));
                    }
                }
                let texture=frontend.mesh_texture_resources.get(&key.texture_id).ok_or_else(||invalid("equipment unowned texture"))?;
                let source=frontend.mesh_texture_assets.get(&key.texture_id).ok_or_else(||invalid("equipment missing texture source"))?;
                let sampler=gal.sampler_descriptor_for_capture(texture.sampler)?;
                let atlas = !key.standard_item_foil && (source.width > 128 || source.height > 128);
                let capture_source = atlas || wolf && !key.standard_item_foil;
                if capture_source && !textures.contains_key(&key.texture_id) {
                    texture_bytes = texture_bytes.checked_add(source.rgba.len())
                        .ok_or_else(||invalid("equipment texture capture size overflow"))?;
                    if texture_bytes > 16 * 1024 * 1024 || textures.len() >= 4 {
                        return Err(invalid("equipment texture capture aggregate limit"));
                    }
                    textures.insert(key.texture_id, cached_texture_source(key.texture_id, source)?);
                }
                // Small direct equipment and glint textures are separate from the
                // historical atlas-source set consumed by trim verification.
                if !wolf && !atlas && !model_textures.contains_key(&key.texture_id) {
                    texture_bytes=texture_bytes.checked_add(source.rgba.len())
                        .ok_or_else(||invalid("equipment texture capture size overflow"))?;
                    if texture_bytes>16*1024*1024 || model_textures.len()>=8 {
                        return Err(invalid("equipment model texture capture aggregate limit"));
                    }
                    model_textures.insert(key.texture_id,cached_texture_source(key.texture_id,source)?);
                }
                let mesh_uvs = if capture_source {
                    if wolf {captured_mesh_uvs_bounded(&asset.vertex_bytes,512)?}
                    else {captured_mesh_uvs(&asset.vertex_bytes)?}
                } else { Vec::new() };

                if !descriptor.bindings.iter().any(|b|b.binding==2 && b.resource==texture.view)
                    || !descriptor.bindings.iter().any(|b|b.binding==3 && b.resource==texture.sampler) {
                    return Err(invalid("equipment texture/sampler binding mismatch"));
                }
                let ib=descriptor.bindings.iter().find(|b|b.binding==1).ok_or_else(||invalid("equipment missing instance binding"))?;
                let count=*instances as usize;
                let size=WORLD_MESH_BATCH_HEADER_BYTES+count*WORLD_MESH_INSTANCE_BYTES;
                if ib.dynamic_offsets!=[0] || ib.buffer_range.is_none_or(|v|v<size as u64) {
                    return Err(invalid("equipment instance range mismatch"));
                }
                let transforms=uploaded_range(&ops[..op_index],ib.resource,offsets[1],size)?;
                let payload=if key.standard_item_foil {
                    if key.decal_vertex_count!=0 {return Err(invalid("equipment unexpected decal"));}
                    let fb=descriptor.bindings.iter().find(|b|b.binding==4).ok_or_else(||invalid("equipment missing foil binding"))?;
                    if fb.dynamic_offsets!=[0] || fb.buffer_range.is_none_or(|v|v<(count*48) as u64) {
                        return Err(invalid("equipment foil range mismatch"));
                    }
                    Some(uploaded_range(&ops[..op_index],fb.resource,offsets[2],count*48)?)
                } else {None};
                let mut uploaded=Vec::new();
                for i in 0..count {
                    let start=WORLD_MESH_BATCH_HEADER_BYTES+i*WORLD_MESH_INSTANCE_BYTES;
                    uploaded.push(json!({"model_pose":floats(&transforms[start..start+64])?,
                        "color":floats(&transforms[start+64..start+80])?,
                        "material":floats(&transforms[start+80..start+96])?,
                        "foil":payload.map(|p|floats(&p[i*48..(i+1)*48])).transpose()?}));
                }
                draws.push(json!({"command_index":op_index,"mesh_key":key.mesh_key,
                    "mesh_generation":key.mesh_generation,"section_index":key.section_index,
                    "mesh_vertex_bytes":asset.vertex_bytes.len(),
                    "mesh_vertex_xxh32":format!("{:08x}",xxh32(&asset.vertex_bytes,0)),
                    "mesh_uvs":mesh_uvs,
                    "geometry_bindings_verified":true,
                    "geometry_uploads":geometry_uploads,
                    "mesh_source_geometry":captured_mesh_geometry(&asset.vertex_bytes,&asset.index_bytes,asset.index_type)?,
                    "projection":projection.map(|p|format!("{p:?}")),"entity_identity":asset.entity_identity,"view_matrix":floats(&transforms[..64])?,
                    "material_mode":key.material_mode,"depth_policy":key.depth_policy,
                    "program":program.shader_program_identity.as_str(),
                    "depth_compare":pipeline_desc.depth_compare.map(|v|format!("{v:?}")),
                    "depth_write":pipeline_desc.depth_write,"blend":format!("{:?}",pipeline_desc.blend),
                    "cull_mode":format!("{:?}",pipeline_desc.cull_mode),
                    "front_face":format!("{:?}",pipeline_desc.front_face),
                    "depth_bias":pipeline_desc.depth_bias.map(|v|format!("{v:?}")),
                    "standard_foil":key.standard_item_foil,"texture_id":key.texture_id,
                    "texture_width":source.width,"texture_height":source.height,
                    "texture_rgba_xxh32":format!("{:08x}",xxh32(&source.rgba,0)),
                    "sampler":format!("{sampler:?}"),"index_count":indices,"instance_count":instances,
                    "uploaded_instances":uploaded}));
            }
            _=>{}
        }
    }
    Ok((semantics,draws,textures.into_values().collect(),model_textures.into_values().collect()))
}

// Capture CPU-owned atlas bytes once per resource, only for selected diagnostic
// frames. Limits are independent of compressibility; no GPU handles/readback.
pub(super) fn cached_texture_source(id:u32,source:&WorldMaterialTextureAsset) -> GalResult<Value> {
    if source.equipment_capture_png.get().is_none() {
        let encoded=encoded_texture_source(id,source.width,source.height,&source.rgba)?;
        let hex=encoded["png_hex"].as_str().ok_or_else(||invalid("equipment missing encoded PNG"))?;
        let _=source.equipment_capture_png.set(hex.to_owned());
    }
    Ok(json!({"texture_id":id,"width":source.width,"height":source.height,"encoding":"png-rgba8-hex",
        "rgba_xxh32":format!("{:08x}",xxh32(&source.rgba,0)),
        "png_hex":source.equipment_capture_png.get().ok_or_else(||invalid("equipment missing cached PNG"))?}))
}
fn encoded_texture_source(id:u32,width:u32,height:u32,rgba:&[u8]) -> GalResult<Value> {
    let size=(width as usize).checked_mul(height as usize).and_then(|n|n.checked_mul(4))
        .ok_or_else(||invalid("equipment texture capture size overflow"))?;
    if width==0 || height==0 || size>8*1024*1024 || size!=rgba.len() {
        return Err(invalid("equipment texture capture dimensions"));
    }
    let mut bytes=Vec::new();
    {
        let mut encoder=png::Encoder::new(&mut bytes,width,height);
        encoder.set_color(png::ColorType::Rgba);encoder.set_depth(png::BitDepth::Eight);
        let mut writer=encoder.write_header().map_err(|_|invalid("equipment texture PNG header"))?;
        writer.write_image_data(rgba).map_err(|_|invalid("equipment texture PNG payload"))?;
    }
    if bytes.len()>1024*1024 {return Err(invalid("equipment texture encoded capture limit"));}
    let mut hex=String::with_capacity(bytes.len()*2);
    for byte in bytes {use std::fmt::Write;write!(&mut hex,"{byte:02x}").unwrap();}
    Ok(json!({"texture_id":id,"width":width,"height":height,"encoding":"png-rgba8-hex",
        "rgba_xxh32":format!("{:08x}",xxh32(rgba,0)),"png_hex":hex}))
}
fn ensure_geometry_unwritten(gal:&VulkanicGal,ops:&[CommandOp],buffer:Handle,start:u64,size:usize)->GalResult<()> {
    let end=start.checked_add(size as u64).ok_or_else(||invalid("equipment geometry range overflow"))?;
    let overlaps=|offset:u64,size:u64|size!=0 && start<offset.saturating_add(size) && offset<end;
    for op in ops {
        let writes=match op {
            CommandOp::HostWriteBuffer {buffer:dst,offset,data} => *dst==buffer && overlaps(*offset,data.len() as u64),
            CommandOp::CopyBuffer {dst,size,..} => *dst==buffer && overlaps(0,*size),
            // Conservatively reject texture/programmable writes to this buffer;
            // their potential byte coverage is not the retained host payload.
            CommandOp::CopyTextureToBuffer(region) => region.buffer==buffer,
            CommandOp::BindResourceSet {set,..} => gal.resource_set_descriptor_for_capture(*set)?.bindings.iter()
                .any(|binding|binding.resource==buffer && binding.access.writes()),
            CommandOp::TrackSubmission(_) | CommandOp::BeginPass {..}
            | CommandOp::BindGraphicsPipeline(_) | CommandOp::BindComputePipeline(_)
            | CommandOp::SetVertexBuffer {..} | CommandOp::SetIndexBuffer {..}
            | CommandOp::Draw {..} | CommandOp::DrawIndexed {..} | CommandOp::DrawIndirect {..}
            | CommandOp::Dispatch {..} | CommandOp::DispatchIndirect {..}
            | CommandOp::CopyBufferToTexture(_) | CommandOp::CopyTexture(_)
            | CommandOp::CopyFrameTargetToTexture {..} | CommandOp::CopyTextureToFrameTarget {..}
            | CommandOp::GenerateMipmaps {..} | CommandOp::HostReadBuffer {..}
            | CommandOp::Present {..} | CommandOp::Barrier(_) | CommandOp::EndPass => false,
        };
        if writes {return Err(invalid("equipment geometry written after retained accepted upload"));}
    }
    Ok(())
}

fn captured_mesh_geometry(vertices:&[u8],indices:&[u8],index_type:IndexType) -> GalResult<Value> {
    // Source incarnation only: correspondence with Frozen and actual geometry
    // buffer binding/upload still needs separate verification.
    captured_mesh_uvs_bounded(vertices,512)?;
    let width=match index_type {IndexType::U16=>2,IndexType::U32=>4};
    if indices.is_empty() || indices.len()%width!=0 || indices.len()/width>768 {
        return Err(invalid("equipment mesh index capture limit"));
    }
    let count=vertices.len()/WORLD_MESH_GPU_VERTEX_BYTES;
    for index in indices.chunks_exact(width) {
        let value=if width==2 {u16::from_le_bytes(index.try_into().unwrap()) as u32}
            else {u32::from_le_bytes(index.try_into().unwrap())};
        if value as usize>=count {return Err(invalid("equipment mesh source index out of range"));}
    }
    let hex=|bytes:&[u8]|bytes.iter().map(|v|format!("{v:02x}")).collect::<String>();
    Ok(json!({"encoding":"packed-source-hex-v1","vertex_stride":WORLD_MESH_GPU_VERTEX_BYTES,
        "vertex_hex":hex(vertices),"index_hex":hex(indices),"index_type":format!("{index_type:?}")}))
}

fn captured_mesh_uvs(bytes:&[u8]) -> GalResult<Vec<[f32;2]>> {
    captured_mesh_uvs_bounded(bytes,256)
}
fn captured_mesh_uvs_bounded(bytes:&[u8],limit:usize) -> GalResult<Vec<[f32;2]>> {
    if bytes.is_empty() || bytes.len()%WORLD_MESH_GPU_VERTEX_BYTES!=0
        || bytes.len()/WORLD_MESH_GPU_VERTEX_BYTES>limit {
        return Err(invalid("equipment mesh UV capture limit"));
    }
    bytes.chunks_exact(WORLD_MESH_GPU_VERTEX_BYTES).map(|vertex|{
        let u=f32::from_le_bytes(vertex[12..16].try_into().unwrap());
        let v=f32::from_le_bytes(vertex[28..32].try_into().unwrap());
        if !u.is_finite() || !v.is_finite() {return Err(invalid("equipment non-finite source UV"));}
        Ok([u,v])
    }).collect()
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn wolf_geometry_capture_preserves_bytes_and_rejects_invalid_indices() {
        let mut vertices=vec![0;WORLD_MESH_GPU_VERTEX_BYTES];
        vertices[0..4].copy_from_slice(&0.25f32.to_le_bytes());
        for (kind,width) in [(IndexType::U16,2),(IndexType::U32,4)] {
            let indices=vec![0;width*6];
            let value=captured_mesh_geometry(&vertices,&indices,kind).unwrap();
            assert!(value["vertex_hex"].as_str().unwrap().starts_with("0000803e"));
            assert_eq!(value["vertex_hex"].as_str().unwrap().len(),160);
            assert_eq!(value["index_hex"].as_str().unwrap(),"00".repeat(width*6));
            assert!(captured_mesh_geometry(&vertices,&indices[..indices.len()-1],kind).is_err());
            assert!(captured_mesh_geometry(&vertices,&vec![0;width*769],kind).is_err());
            let mut bad=indices.clone();bad[0]=1;
            assert!(captured_mesh_geometry(&vertices,&bad,kind).is_err());
        }
        assert!(captured_mesh_geometry(&[],&[0,0],IndexType::U16).is_err());
        assert!(captured_mesh_geometry(&vertices,&[],IndexType::U16).is_err());
    }
    #[test]
    fn atlas_capture_preserves_rgba_and_rejects_unbounded_or_malformed_payloads() {
        let rgba=[12,34,56,78,90,123,145,255];
        let value=encoded_texture_source(7,2,1,&rgba).unwrap();
        let hex=value["png_hex"].as_str().unwrap();
        let bytes:Vec<u8>=(0..hex.len()).step_by(2).map(|i|u8::from_str_radix(&hex[i..i+2],16).unwrap()).collect();
        let mut reader=png::Decoder::new(std::io::Cursor::new(bytes)).read_info().unwrap();
        let mut decoded=vec![0;reader.output_buffer_size()];let info=reader.next_frame(&mut decoded).unwrap();
        assert_eq!((info.width,info.height,info.color_type),(2,1,png::ColorType::Rgba));
        assert_eq!(&decoded[..info.buffer_size()],&rgba);
        assert!(encoded_texture_source(7,0,1,&[]).is_err());
        assert!(encoded_texture_source(7,2,1,&rgba[..4]).is_err());
        assert!(encoded_texture_source(7,u32::MAX,u32::MAX,&[]).is_err());
    }
    #[test]
    fn wolf_full_body_capture_has_its_own_bounded_vertex_scope() {
        // Eleven ordinary wolf cubes have 264 face vertices, beyond trim's 256.
        let body=vec![0;264*WORLD_MESH_GPU_VERTEX_BYTES];
        assert!(captured_mesh_uvs(&body).is_err());
        assert_eq!(captured_mesh_uvs_bounded(&body,512).unwrap().len(),264);
        assert!(captured_mesh_uvs_bounded(&vec![0;513*WORLD_MESH_GPU_VERTEX_BYTES],512).is_err());
    }
    #[test]
    fn mesh_uv_capture_reads_packed_uv_lanes_and_rejects_truncation() {
        let mut bytes=vec![0;WORLD_MESH_GPU_VERTEX_BYTES];
        bytes[12..16].copy_from_slice(&0.25f32.to_le_bytes());
        bytes[28..32].copy_from_slice(&0.75f32.to_le_bytes());
        assert_eq!(captured_mesh_uvs(&bytes).unwrap(),vec![[0.25,0.75]]);
        assert!(captured_mesh_uvs(&bytes[..79]).is_err());
        assert!(captured_mesh_uvs(&vec![0;257*WORLD_MESH_GPU_VERTEX_BYTES]).is_err());
        bytes[12..16].copy_from_slice(&f32::NAN.to_le_bytes());
        assert!(captured_mesh_uvs(&bytes).is_err());
    }
}
