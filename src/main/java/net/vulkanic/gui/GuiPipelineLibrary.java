package net.vulkanic.gui;

import net.vulkanic.bridge.VulkanicGalBridge;

public final class GuiPipelineLibrary {
	public static final String VERTEX_SHADER_OPENGL = """
			#version 330 core
		struct PackedGuiSprite {
		    vec4 rect;
		    vec4 viewport;
		    vec4 uv_region;
		    vec4 color;
		};
		layout(std140) uniform GuiSpriteBatch {
		    PackedGuiSprite sprites[256];
		};
			out vec2 v_uv;
			out vec2 v_sprite_corner;
			out vec4 v_color;
			flat out vec4 v_uv_region;
		const vec2 corner[6] = vec2[6](
		    vec2(0.0, 0.0),
		    vec2(1.0, 0.0),
		    vec2(1.0, 1.0),
		    vec2(1.0, 1.0),
		    vec2(0.0, 1.0),
		    vec2(0.0, 0.0)
		);
		void main() {
		    int vertex = gl_VertexID;
		    PackedGuiSprite sprite = sprites[gl_InstanceID];
		    vec2 pixel = sprite.rect.xy + corner[vertex] * sprite.rect.zw;
		    vec2 ndc = vec2((pixel.x / sprite.viewport.x) * 2.0 - 1.0, 1.0 - (pixel.y / sprite.viewport.y) * 2.0);
			    gl_Position = vec4(ndc, 0.0, 1.0);
			    v_uv_region = sprite.uv_region;
			    v_sprite_corner = corner[vertex];
			    v_uv = vec2(
			        sprite.uv_region.x + corner[vertex].x * sprite.uv_region.z,
			        sprite.uv_region.y + (1.0 - corner[vertex].y) * sprite.uv_region.w
		    );
		    v_color = sprite.color;
		}
		""";

	public static final String FRAGMENT_SHADER_OPENGL = """
			#version 330 core
			uniform sampler2D Sampler0;
			in vec2 v_uv;
			in vec2 v_sprite_corner;
			in vec4 v_color;
			flat in vec4 v_uv_region;
			out vec4 out_color;
			void main() {
			    ivec2 texture_size = textureSize(Sampler0, 0);
			    ivec2 origin = ivec2(round(v_uv_region.xy * vec2(texture_size)));
			    ivec2 extent = max(ivec2(round(v_uv_region.zw * vec2(texture_size))), ivec2(1));
			    ivec2 local = clamp(ivec2(floor(v_sprite_corner * vec2(extent))), ivec2(0), extent - ivec2(1));
			    ivec2 texel = ivec2(origin.x + local.x, origin.y + extent.y - 1 - local.y);
			    texel = clamp(texel, ivec2(0), texture_size - ivec2(1));
			    vec4 color = texelFetch(Sampler0, texel, 0) * v_color;
			    if (color.a <= 0.0) {
			        discard;
		    }
		    out_color = color;
		}
		""";

	static VulkanicGalBridge.ResourceBatchBuilder pipeline(
		VulkanicGalBridge.ResourceBatchBuilder builder,
		RustGalGuiRenderer.TextureGroup textureGroup,
		long id,
		String label,
		long pipelineLayout,
		long vertex,
		long fragment
	) {
		if (textureGroup.invertBlend) {
			return builder.graphicsPipelineInvertBlend(id, label, pipelineLayout, vertex, fragment);
		}
		return builder.graphicsPipelineAlphaBlend(id, label, pipelineLayout, vertex, fragment);
	}

	private GuiPipelineLibrary() {
	}
}
