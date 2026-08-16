package com.seibel.distanthorizons.core.dataObjects.transformers;

import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.dataObjects.render.columnViews.ColumnArrayView;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullDataToRenderDataTransformerSemanticMaterialTest {
	@Test
	void semanticVerticalReductionOnlyMergesIdenticalSourceIdentities() {
		assertTrue(FullDataToRenderDataTransformer.sameSemanticMaterial(7, 7));
		assertFalse(FullDataToRenderDataTransformer.sameSemanticMaterial(7, 8));
		assertFalse(FullDataToRenderDataTransformer.sameSemanticMaterial(7, -1));
	}

	@Test
	void semanticVerticalReductionRetainsEachSourceMaterialIntervalForFutureExactFaceExpansion() {
		LongArrayList source = new LongArrayList(new long[] {
			RenderDataPointUtil.createDataPoint(4, 0, 0xFFAA2211, 15, 0, 2),
			RenderDataPointUtil.createDataPoint(8, 4, 0xFF22AA11, 15, 0, 13)
		});
		List<ColumnRenderSource.SemanticMaterialSpan> spans =
			FullDataToRenderDataTransformer.reducedSemanticMaterialSpans(
				new ColumnArrayView(source, 2, 0, 2),
				new int[] {9, 12},
				new byte[] {ColumnRenderSource.SEMANTIC_VARIANT_EXACT, ColumnRenderSource.SEMANTIC_VARIANT_EXACT},
				new long[] {100L, 200L},
				RenderDataPointUtil.createDataPoint(8, 0, 0xFF777777, 15, 0, 2)
			);

		assertEquals(2, spans.size());
		assertEquals(new ColumnRenderSource.SemanticMaterialSpan(0, 4, 9,
			ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 100L), spans.get(0));
		assertEquals(new ColumnRenderSource.SemanticMaterialSpan(4, 8, 12,
			ColumnRenderSource.SEMANTIC_VARIANT_EXACT, 200L), spans.get(1));
	}
}
