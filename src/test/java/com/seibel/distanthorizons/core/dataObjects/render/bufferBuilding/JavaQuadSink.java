package com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.enums.EDhDirection;

/** Quad target for the Java reference geometry: the Java lists or the native builder. */
interface JavaQuadSink
{
	void addQuadAdj(EDhDirection dir, short x, short y, short z, short widthEastWest, short widthNorthSouthOrUpDown,
		int color, byte irisBlockMaterialId, byte skyLight, byte blockLight,
		int semanticMaterialId, byte semanticVariantState, long semanticVariantPosition);
	void addQuadUp(short minX, short maxY, short minZ, short widthEastWest, short widthNorthSouthOrUpDown,
		int color, byte irisBlockMaterialId, byte skylight, byte blocklight,
		int semanticMaterialId, byte semanticVariantState, long semanticVariantPosition);
	void addQuadDown(short x, short y, short z, short width, short wz,
		int color, byte irisBlockMaterialId, byte skylight, byte blocklight,
		int semanticMaterialId, byte semanticVariantState, long semanticVariantPosition);
	int internSemanticMaterial(ColumnRenderSource.SemanticMaterialIdentity identity);
	void mergeQuads();

	/** Forwards the Java reference geometry into a native builder. */
	static JavaQuadSink of(LodQuadBuilder builder)
	{
		return new JavaQuadSink()
		{
			public void addQuadAdj(EDhDirection dir, short x, short y, short z, short w, short h, int color, byte material,
				byte sky, byte block, int id, byte variant, long position)
			{ builder.addQuadAdj(dir, x, y, z, w, h, color, material, sky, block, id, variant, position); }
			public void addQuadUp(short x, short y, short z, short w, short h, int color, byte material,
				byte sky, byte block, int id, byte variant, long position)
			{ builder.addQuadUp(x, y, z, w, h, color, material, sky, block, id, variant, position); }
			public void addQuadDown(short x, short y, short z, short w, short h, int color, byte material,
				byte sky, byte block, int id, byte variant, long position)
			{ builder.addQuadDown(x, y, z, w, h, color, material, sky, block, id, variant, position); }
			public int internSemanticMaterial(ColumnRenderSource.SemanticMaterialIdentity identity)
			{ return builder.internSemanticMaterial(identity); }
			public void mergeQuads() { builder.mergeQuads(); }
		};
	}
}
