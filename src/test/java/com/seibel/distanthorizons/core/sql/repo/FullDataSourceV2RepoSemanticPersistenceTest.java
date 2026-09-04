package com.seibel.distanthorizons.core.sql.repo;

import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullDataSourceV2RepoSemanticPersistenceTest {
	@Test
	void migrationAndRepoRoundTripRetainV4ContributorBlob(@TempDir Path tempDir) throws Exception {
		Path database = tempDir.resolve("full-data.sqlite");
		try (FullDataSourceV2Repo repo = new FullDataSourceV2Repo("jdbc:sqlite", database.toFile())) {
			try (Statement statement = repo.getConnection().createStatement();
				 ResultSet columns = statement.executeQuery("PRAGMA table_info(FullData)")) {
				boolean found = false;
				while (columns.next()) found |= "SemanticHorizontalContributorData".equals(columns.getString("name"));
				assertTrue(found, "V4 contributor column must be installed by the migration");
			}

			FullDataSourceV2DTO dto = FullDataSourceV2DTO.CreateEmptyDataSourceForDecoding();
			dto.pos = DhSectionPos.encode(DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL, 0, 0);
			dto.dataFormatVersion = (byte) FullDataSourceV2DTO.DATA_FORMAT.V4_LATEST;
			dto.compressionModeValue = 0;
			byte[] payload = { 3, 1, 4, 1, 5, 9 };
			dto.compressedSemanticHorizontalContributorByteArray.addElements(0, payload);
			repo.save(dto);

			FullDataSourceV2DTO decoded = repo.getByKey(dto.pos);
			assertNotNull(decoded);
			assertArrayEquals(payload, Arrays.copyOf(
				decoded.compressedSemanticHorizontalContributorByteArray.elements(),
				decoded.compressedSemanticHorizontalContributorByteArray.size()));
			decoded.close();
			dto.close();
		}
	}
}
