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
import static org.junit.jupiter.api.Assertions.assertEquals;

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

	@Test
	void v4DtoRoundTripPreservesBoundedHorizontalContributors() throws Exception {
		long pos = DhSectionPos.encode(
			(byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1), 0, 0
		);
		FullDataSourceV2 source = FullDataSourceV2.createEmpty(pos);
		try {
			it.unimi.dsi.fastutil.longs.LongArrayList[] contributors = new it.unimi.dsi.fastutil.longs.LongArrayList[] {
				new it.unimi.dsi.fastutil.longs.LongArrayList(new long[] { 7L }), null,
				new it.unimi.dsi.fastutil.longs.LongArrayList(new long[] { 9L }), null
			};
			source.setSemanticHorizontalContributors(2, 3, contributors);
			FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateFromDataSource(
				source, EDhApiDataCompressionMode.UNCOMPRESSED
			);
			try {
				FullDataSourceV2 decoded = dto.createUnitTestDataSource();
				try {
					assertEquals(7L, decoded.getSemanticHorizontalContributors(2, 3)[0].getLong(0));
					assertEquals(9L, decoded.getSemanticHorizontalContributors(2, 3)[2].getLong(0));
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

	@Test
	void v3DtoLoadDoesNotRequireTheV4ContributorSidecar() throws Exception {
		long pos = DhSectionPos.encode(
			(byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1), 0, 0
		);
		FullDataSourceV2 source = FullDataSourceV2.createEmpty(pos);
		try {
			FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateFromDataSource(
				source, EDhApiDataCompressionMode.UNCOMPRESSED
			);
			try {
				dto.dataFormatVersion = (byte) FullDataSourceV2DTO.DATA_FORMAT.V3_LATEST;
				dto.compressedSemanticHorizontalContributorByteArray.clear();
				FullDataSourceV2 decoded = dto.createUnitTestDataSource();
				decoded.close();
			} finally {
				dto.close();
			}
		} finally {
			source.close();
		}
	}

	@Test
	void v4DtoContributorColumnsAreBoundedPerColumn() throws Exception {
		long pos = DhSectionPos.encode(
			(byte) (DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 1), 0, 0
		);
		FullDataSourceV2 source = FullDataSourceV2.createEmpty(pos);
		try {
			it.unimi.dsi.fastutil.longs.LongArrayList points = new it.unimi.dsi.fastutil.longs.LongArrayList();
			for (int i = 0; i < FullDataSourceV2.MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_POINTS + 32; i++) {
				points.add(i);
			}
			source.setSemanticHorizontalContributors(0, 0,
				new it.unimi.dsi.fastutil.longs.LongArrayList[] { points, null, null, null });
			FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateFromDataSource(
				source, EDhApiDataCompressionMode.UNCOMPRESSED
			);
			try {
				FullDataSourceV2 decoded = dto.createUnitTestDataSource();
				try {
					assertEquals(FullDataSourceV2.MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_POINTS,
						decoded.getSemanticHorizontalContributors(0, 0)[0].size());
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
