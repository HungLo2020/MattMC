package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RenderDataPointReducingListSemanticMaterialTest {
	@Test
	void semanticReductionMarksDifferentVisibleMaterialsMixed() {
		LongArrayList source = new LongArrayList(new long[] {
			RenderDataPointUtil.createDataPoint(1, 0, 0xFF553311, 12, 0, 2),
			RenderDataPointUtil.createDataPoint(2, 1, 0xFF449922, 15, 0, 13)
		});
		ColumnArrayView sourceView = new ColumnArrayView(source, 2, 0, 2);
		LongArrayList output = new LongArrayList(new long[] {0L});
		ColumnArrayView outputView = new ColumnArrayView(output, 1, 0, 1);
		int[] outputMaterials = new int[1];

		RenderDataPointUtil.mergeMultiData(sourceView, new int[] {31, 47}, outputView, outputMaterials);

		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_MIXED, outputMaterials[0]);
		assertEquals(2, RenderDataPointUtil.getYMax(outputView.get(0)));
	}

	@Test
	void semanticReductionRetainsOneExactMaterialWhenEveryVisibleContributorMatches() {
		LongArrayList source = new LongArrayList(new long[] {
			RenderDataPointUtil.createDataPoint(1, 0, 0xFF553311, 12, 0, 2),
			RenderDataPointUtil.createDataPoint(2, 1, 0xFF449922, 15, 0, 13)
		});
		ColumnArrayView sourceView = new ColumnArrayView(source, 2, 0, 2);
		LongArrayList output = new LongArrayList(new long[] {0L});
		ColumnArrayView outputView = new ColumnArrayView(output, 1, 0, 1);
		int[] outputMaterials = new int[1];

		RenderDataPointUtil.mergeMultiData(sourceView, new int[] {47, 47}, outputView, outputMaterials);

		assertEquals(47, outputMaterials[0]);
		assertEquals(2, RenderDataPointUtil.getYMax(outputView.get(0)));
	}

	@Test
	void semanticReductionMarksMixedWhenErasingTheMiddleSegment() {
		LongArrayList source = new LongArrayList(new long[] {
			dataPoint(255, 1, 0),
			dataPoint(128, 2, 1),
			dataPoint(255, 3, 2)
		});
		ColumnArrayView sourceView = new ColumnArrayView(source, 3, 0, 3);
		LongArrayList output = new LongArrayList(new long[] {0L});
		ColumnArrayView outputView = new ColumnArrayView(output, 1, 0, 1);
		int[] outputMaterials = new int[1];

		RenderDataPointUtil.mergeMultiData(sourceView, new int[] {31, 47, 59}, outputView, outputMaterials);

		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_MIXED, outputMaterials[0]);
		assertEquals(3, RenderDataPointUtil.getYMax(outputView.get(0)));
	}

	@Test
	void semanticReductionMarksMixedWhenForcedToMergeTheBottomSegment() {
		LongArrayList source = new LongArrayList(new long[] {
			dataPoint(255, 1, 0),
			dataPoint(128, 2, 1),
			dataPoint(64, 3, 2)
		});
		ColumnArrayView sourceView = new ColumnArrayView(source, 3, 0, 3);
		LongArrayList output = new LongArrayList(new long[] {0L});
		ColumnArrayView outputView = new ColumnArrayView(output, 1, 0, 1);
		int[] outputMaterials = new int[1];

		RenderDataPointUtil.mergeMultiData(sourceView, new int[] {31, 47, 59}, outputView, outputMaterials);

		assertEquals(ColumnRenderSource.SEMANTIC_MATERIAL_MIXED, outputMaterials[0]);
		assertEquals(3, RenderDataPointUtil.getYMax(outputView.get(0)));
	}

	private static long dataPoint(int alpha, int height, int depth) {
		return RenderDataPointUtil.createDataPoint(alpha, 0x55, 0x88, 0x22, height, depth, 12, 0, 2);
	}
}
