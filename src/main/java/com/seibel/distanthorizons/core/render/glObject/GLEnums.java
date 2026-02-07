package com.seibel.distanthorizons.core.render.glObject;

import net.vulkanic.VulkanicAPI;

// Turns GL int enums back to readable strings
public class GLEnums
{
	
	public static String getString(int glEnum)
	{
		// blend stuff
		switch (glEnum)
		{
			case VulkanicAPI.GL_ZERO:
				return "GL_ZERO";
			case VulkanicAPI.GL_ONE:
				return "GL_ONE";
			case VulkanicAPI.GL_SRC_COLOR:
				return "GL_SRC_COLOR";
			case VulkanicAPI.GL_ONE_MINUS_SRC_COLOR:
				return "GL_ONE_MINUS_SRC_COLOR";
			case VulkanicAPI.GL_DST_COLOR:
				return "GL_DST_COLOR";
			case VulkanicAPI.GL_ONE_MINUS_DST_COLOR:
				return "GL_ONE_MINUS_DST_COLOR";
			case VulkanicAPI.GL_SRC_ALPHA:
				return "GL_SRC_ALPHA";
			case VulkanicAPI.GL_ONE_MINUS_SRC_ALPHA:
				return "GL_ONE_MINUS_SRC_ALPHA";
			case VulkanicAPI.GL_DST_ALPHA:
				return "GL_DST_ALPHA";
			case VulkanicAPI.GL_ONE_MINUS_DST_ALPHA:
				return "GL_ONE_MINUS_DST_ALPHA";
			case VulkanicAPI.GL_CONSTANT_COLOR:
				return "GL_CONSTANT_COLOR";
			case VulkanicAPI.GL_ONE_MINUS_CONSTANT_COLOR:
				return "GL_ONE_MINUS_CONSTANT_COLOR";
			case VulkanicAPI.GL_CONSTANT_ALPHA:
				return "GL_CONSTANT_ALPHA";
			case VulkanicAPI.GL_ONE_MINUS_CONSTANT_ALPHA:
				return "GL_ONE_MINUS_CONSTANT_ALPHA";
			default:
		}
		
		// shader stuff
		switch (glEnum)
		{
			case VulkanicAPI.GL_VERTEX_SHADER:
				return "GL_VERTEX_SHADER";
			case VulkanicAPI.GL_GEOMETRY_SHADER:
				return "GL_GEOMETRY_SHADER";
			case VulkanicAPI.GL_FRAGMENT_SHADER:
				return "GL_FRAGMENT_SHADER";
			default:
		}
		
		// stencil stuff
		switch (glEnum)
		{
			case VulkanicAPI.GL_KEEP:
				return "GL_KEEP";
			case VulkanicAPI.GL_ZERO:
				return "GL_ZERO";
			case VulkanicAPI.GL_REPLACE:
				return "GL_REPLACE";
			case VulkanicAPI.GL_INCR:
				return "GL_INCR";
			case VulkanicAPI.GL_DECR:
				return "GL_DECR";
			case VulkanicAPI.GL_INVERT:
				return "GL_INVERT";
			case VulkanicAPI.GL_INCR_WRAP:
				return "GL_INCR_WRAP";
			case VulkanicAPI.GL_DECR_WRAP:
				return "GL_DECR_WRAP";
			default:
		}
		
		// depth stuff
		switch (glEnum)
		{
			case VulkanicAPI.GL_NEVER:
				return "GL_NEVER";
			case VulkanicAPI.GL_LESS:
				return "GL_LESS";
			case VulkanicAPI.GL_EQUAL:
				return "GL_EQUAL";
			case VulkanicAPI.GL_LEQUAL:
				return "GL_LEQUAL";
			case VulkanicAPI.GL_GREATER:
				return "GL_GREATER";
			case VulkanicAPI.GL_NOTEQUAL:
				return "GL_NOTEQUAL";
			case VulkanicAPI.GL_GEQUAL:
				return "GL_GEQUAL";
			case VulkanicAPI.GL_ALWAYS:
				return "GL_ALWAYS";
			default:
		}
		
		// Texture binding points
		switch (glEnum)
		{
			case VulkanicAPI.GL_TEXTURE0:
				return "GL_TEXTURE0";
			case VulkanicAPI.GL_TEXTURE1:
				return "GL_TEXTURE1";
			case VulkanicAPI.GL_TEXTURE2:
				return "GL_TEXTURE2";
			case VulkanicAPI.GL_TEXTURE3:
				return "GL_TEXTURE3";
			case VulkanicAPI.GL_TEXTURE4:
				return "GL_TEXTURE4";
			case VulkanicAPI.GL_TEXTURE5:
				return "GL_TEXTURE5";
			case VulkanicAPI.GL_TEXTURE6:
				return "GL_TEXTURE6";
			case VulkanicAPI.GL_TEXTURE7:
				return "GL_TEXTURE7";
			case VulkanicAPI.GL_TEXTURE8:
				return "GL_TEXTURE8";
			case VulkanicAPI.GL_TEXTURE9:
				return "GL_TEXTURE9";
			case VulkanicAPI.GL_TEXTURE10:
				return "GL_TEXTURE10";
			case VulkanicAPI.GL_TEXTURE11:
				return "GL_TEXTURE11";
			case VulkanicAPI.GL_TEXTURE12:
				return "GL_TEXTURE12";
			case VulkanicAPI.GL_TEXTURE13:
				return "GL_TEXTURE13";
			case VulkanicAPI.GL_TEXTURE14:
				return "GL_TEXTURE14";
			case VulkanicAPI.GL_TEXTURE15:
				return "GL_TEXTURE15";
			case VulkanicAPI.GL_TEXTURE16:
				return "GL_TEXTURE16";
			case VulkanicAPI.GL_TEXTURE17:
				return "GL_TEXTURE17";
			case VulkanicAPI.GL_TEXTURE18:
				return "GL_TEXTURE18";
			case VulkanicAPI.GL_TEXTURE19:
				return "GL_TEXTURE19";
			case VulkanicAPI.GL_TEXTURE20:
				return "GL_TEXTURE20";
			case VulkanicAPI.GL_TEXTURE21:
				return "GL_TEXTURE21";
			case VulkanicAPI.GL_TEXTURE22:
				return "GL_TEXTURE22";
			case VulkanicAPI.GL_TEXTURE23:
				return "GL_TEXTURE23";
			case VulkanicAPI.GL_TEXTURE24:
				return "GL_TEXTURE24";
			case VulkanicAPI.GL_TEXTURE25:
				return "GL_TEXTURE25";
			case VulkanicAPI.GL_TEXTURE26:
				return "GL_TEXTURE26";
			case VulkanicAPI.GL_TEXTURE27:
				return "GL_TEXTURE27";
			case VulkanicAPI.GL_TEXTURE28:
				return "GL_TEXTURE28";
			case VulkanicAPI.GL_TEXTURE29:
				return "GL_TEXTURE29";
			case VulkanicAPI.GL_TEXTURE30:
				return "GL_TEXTURE30";
			case VulkanicAPI.GL_TEXTURE31:
				return "GL_TEXTURE31";
			default:
		}
		
		// Polygon modes
		switch (glEnum)
		{
			case VulkanicAPI.GL_POINT:
				return "GL_POINT";
			case VulkanicAPI.GL_LINE:
				return "GL_LINE";
			case VulkanicAPI.GL_FILL:
				return "GL_FILL";
			default:
		}
		
		// Culling modes
		switch (glEnum)
		{
			case VulkanicAPI.GL_FRONT:
				return "GL_FRONT";
			case VulkanicAPI.GL_BACK:
				return "GL_BACK";
			case VulkanicAPI.GL_FRONT_AND_BACK:
				return "GL_FRONT_AND_BACK";
			default:
		}
		
		// Types
		switch (glEnum)
		{
			case VulkanicAPI.GL_BYTE:
				return "GL_BYTE";
			case VulkanicAPI.GL_UNSIGNED_BYTE:
				return "GL_UNSIGNED_BYTE";
			case VulkanicAPI.GL_SHORT:
				return "GL_SHORT";
			case VulkanicAPI.GL_UNSIGNED_SHORT:
				return "GL_UNSIGNED_SHORT";
			case VulkanicAPI.GL_INT:
				return "GL_INT";
			case VulkanicAPI.GL_UNSIGNED_INT:
				return "GL_UNSIGNED_INT";
			case VulkanicAPI.GL_FLOAT:
				return "GL_FLOAT";
			case VulkanicAPI.GL_DOUBLE:
				return "GL_DOUBLE";
			default:
		}
		
		return "GL_UNKNOWN(" + glEnum + ")";
	}
	
	public static int getTypeSize(int glTypeEnum)
	{
		switch (glTypeEnum)
		{
			case VulkanicAPI.GL_BYTE:
			case VulkanicAPI.GL_UNSIGNED_BYTE:
				return 1;
			case VulkanicAPI.GL_SHORT:
			case VulkanicAPI.GL_UNSIGNED_SHORT:
				return 2;
			case VulkanicAPI.GL_INT:
			case VulkanicAPI.GL_UNSIGNED_INT:
				return 4;
			case VulkanicAPI.GL_FLOAT:
				return 4;
			case VulkanicAPI.GL_DOUBLE:
				return 8;
			default:
				throw new IllegalArgumentException("Unknown type enum: " + getString(glTypeEnum));
		}
	}
	
}