package com.seibel.distanthorizons.core.sql.dto;

import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.api.enums.config.EDhApiWorldCompressionMode;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiWorldGenerationStep;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullDataSourceV2SemanticUniformityTest {
	@Test
	void v3DtoRoundTripPreservesUniformityProof() throws Exception {
		long pos = DhSectionPos.encode(
			(byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1), 0, 0
		);
		FullDataSourceV2 source = FullDataSourceV2.createEmpty(pos);
		try {
			source.setSingleColumn(
				new LongArrayList(new long[] { 0L }), 3, 5,
				EDhApiWorldGenerationStep.DOWN_SAMPLED,
				EDhApiWorldCompressionMode.MERGE_SAME_BLOCKS
			);
			source.setSemanticHorizontalUniformity(3, 5, true);
			FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateFromDataSource(
				source, EDhApiDataCompressionMode.UNCOMPRESSED
			);
			try {
				FullDataSourceV2 decoded = dto.createUnitTestDataSource();
				try {
					assertTrue(decoded.hasSemanticHorizontalUniformity(3, 5));
					assertFalse(decoded.hasSemanticHorizontalUniformity(4, 5));
				} finally {
					decoded.close();
				}
			} finally {
				dto.close();
			}
		} finally {
			source.close();
		}
	}
}
