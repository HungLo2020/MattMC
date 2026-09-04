package com.seibel.distanthorizons.core.dataObjects.fullData.sources;

import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.FullDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FullDataSourceV2ContributorReplacementTest {
	@Test
	void sameDetailReplacementRetainsBoundedContributorFootprint() throws Exception {
		long pos = DhSectionPos.encode(
			(byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1), 0, 0
		);
		FullDataSourceV2 incoming = FullDataSourceV2.createEmpty(pos);
		FullDataSourceV2 target = FullDataSourceV2.createEmpty(pos);
		try {
			incoming.setSemanticHorizontalUniformity(3, 5, false);
			long point = FullDataPointUtil.encode(0, 8, 0, (byte) 0, (byte) 15);
			incoming.setSingleColumn(new LongArrayList(new long[] {point}), 3, 5,
				EDhApiWorldGenerationStep.SURFACE, EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS);
			incoming.setSemanticHorizontalContributors(3, 5, new LongArrayList[] {
				new LongArrayList(new long[] {point}), null,
				new LongArrayList(new long[] {point}), null
			});

			Method replacement = FullDataSourceV2.class.getDeclaredMethod(
				"updateFromSameDetailLevel", FullDataSourceV2.class, int[].class
			);
			replacement.setAccessible(true);
			replacement.invoke(target, incoming, new int[] {0});

			assertFalse(target.hasSemanticHorizontalUniformity(3, 5));
			LongArrayList[] copied = target.getSemanticHorizontalContributors(3, 5);
			assertNotNull(copied);
			assertEquals(point, copied[0].getLong(0));
			assertEquals(point, copied[2].getLong(0));
		} finally {
			target.close();
			incoming.close();
		}
	}
}
