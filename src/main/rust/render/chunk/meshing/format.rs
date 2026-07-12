use super::*;

#[derive(Clone, Copy, PartialEq, Eq)]
pub(super) struct NativeFormat {
    pub(super) vertex_stride: usize,
    pub(super) block_id_offset: usize,
    pub(super) normal_offset: usize,
    pub(super) tangent_offset: usize,
    pub(super) mid_uv_offset: usize,
    pub(super) mid_block_offset: usize,
    pub(super) section_index: i32,
    pub(super) separate_ao: bool,
}

impl NativeFormat {
    pub(super) fn from_abi(
        quad_stride: i32,
        vertex_stride: i32,
        block_id_offset: i32,
        normal_offset: i32,
        tangent_offset: i32,
        mid_uv_offset: i32,
        mid_block_offset: i32,
        section_index: i32,
        separate_ao: i32,
    ) -> Result<Self, i32> {
        let quad_stride = usize::try_from(quad_stride).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let vertex_stride = usize::try_from(vertex_stride).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let block_id_offset = usize::try_from(block_id_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let normal_offset = usize::try_from(normal_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let tangent_offset = usize::try_from(tangent_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let mid_uv_offset = usize::try_from(mid_uv_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let mid_block_offset =
            usize::try_from(mid_block_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;

        if quad_stride != std::mem::size_of::<NativeQuad>() || vertex_stride < 20 {
            return Err(ERR_INVALID_ARGUMENT);
        }

        for offset in [
            block_id_offset,
            normal_offset,
            tangent_offset,
            mid_uv_offset,
            mid_block_offset,
        ] {
            if offset != 0 && offset + 4 > vertex_stride {
                return Err(ERR_INVALID_ARGUMENT);
            }
        }

        Ok(Self {
            vertex_stride,
            block_id_offset,
            normal_offset,
            tangent_offset,
            mid_uv_offset,
            mid_block_offset,
            section_index,
            separate_ao: separate_ao != 0,
        })
    }
}

pub fn verify() -> i32 {
    if std::mem::size_of::<QuadVertex>() == 32
        && std::mem::size_of::<NativeQuad>() == 152
        && std::mem::size_of::<FlatQuadRecord>() == 156
        && std::mem::size_of::<LightBlockRecord>() == 24
        && std::mem::size_of::<FluidFaceRecord>() == 172
        && std::mem::size_of::<StaticModelVertexRecord>() == 28
        && std::mem::size_of::<StaticModelQuadRecord>() == 160
        && std::mem::size_of::<StaticModelBlockRecord>() == 52
        && std::mem::size_of::<NativeSectionBlockRecord>() == 316
        && std::mem::size_of::<NativeModelSelectorEntry>() == 8
    {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
}

pub(super) fn compact_format_value(value: i32) -> i32 {
    match value {
        COMPACT_VALUE_STRIDE => COMPACT_VERTEX_STRIDE,
        COMPACT_VALUE_POSITION_OFFSET => COMPACT_POSITION_OFFSET,
        COMPACT_VALUE_COLOR_OFFSET => COMPACT_COLOR_OFFSET,
        COMPACT_VALUE_TEXTURE_OFFSET => COMPACT_TEXTURE_OFFSET,
        COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET => COMPACT_LIGHT_MATERIAL_INDEX_OFFSET,
        COMPACT_VALUE_BLOCK_ID_OFFSET => COMPACT_NATIVE_BLOCK_ID_OFFSET,
        COMPACT_VALUE_NORMAL_OFFSET => COMPACT_NATIVE_NORMAL_OFFSET,
        COMPACT_VALUE_TANGENT_OFFSET => COMPACT_NATIVE_TANGENT_OFFSET,
        COMPACT_VALUE_MID_UV_OFFSET => COMPACT_NATIVE_MID_UV_OFFSET,
        COMPACT_VALUE_MID_BLOCK_OFFSET => COMPACT_NATIVE_MID_BLOCK_OFFSET,
        COMPACT_VALUE_POSITION_MAX_VALUE => POSITION_MAX_VALUE as i32,
        COMPACT_VALUE_TEXTURE_MAX_VALUE => TEXTURE_MAX_VALUE as i32,
        _ => ERR_INVALID_ARGUMENT,
    }
}
