package net.minecraft.world.scores;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.saveddata.SavedData;

public class ScoreboardSaveData extends SavedData {
	public static final String FILE_ID = "scoreboard";
	private final Scoreboard scoreboard;

	public ScoreboardSaveData(Scoreboard scoreboard) {
		this.scoreboard = scoreboard;
	}

	public void loadFrom(ScoreboardSaveData.Packed packed) {
		packed.objectives().forEach(this.scoreboard::loadObjective);
		packed.scores().forEach(this.scoreboard::loadPlayerScore);
		packed.displaySlots().forEach((displaySlot, string) -> {
			Objective objective = this.scoreboard.getObjective(string);
			this.scoreboard.setDisplayObjective(displaySlot, objective);
		});
		packed.teams().forEach(this.scoreboard::loadPlayerTeam);
	}

	public ScoreboardSaveData.Packed pack() {
		Map<DisplaySlot, String> map = new EnumMap(DisplaySlot.class);

		for (DisplaySlot displaySlot : DisplaySlot.values()) {
			Objective objective = this.scoreboard.getDisplayObjective(displaySlot);
			if (objective != null) {
				map.put(displaySlot, objective.getName());
			}
		}

		return new ScoreboardSaveData.Packed(
			this.scoreboard.getObjectives().stream().map(Objective::pack).toList(),
			this.scoreboard.packPlayerScores(),
			map,
			this.scoreboard.getPlayerTeams().stream().map(PlayerTeam::pack).toList()
		);
	}

	public record Packed(
		List<Objective.Packed> objectives, List<Scoreboard.PackedScore> scores, Map<DisplaySlot, String> displaySlots, List<PlayerTeam.Packed> teams
	) {
		public static final Codec<ScoreboardSaveData.Packed> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					Objective.Packed.CODEC.listOf().optionalFieldOf("Objectives", List.of()).forGetter(ScoreboardSaveData.Packed::objectives),
					Scoreboard.PackedScore.CODEC.listOf().optionalFieldOf("PlayerScores", List.of()).forGetter(ScoreboardSaveData.Packed::scores),
					Codec.unboundedMap(DisplaySlot.CODEC, Codec.STRING).optionalFieldOf("DisplaySlots", Map.of()).forGetter(ScoreboardSaveData.Packed::displaySlots),
					PlayerTeam.Packed.CODEC.listOf().optionalFieldOf("Teams", List.of()).forGetter(ScoreboardSaveData.Packed::teams)
				)
				.apply(instance, ScoreboardSaveData.Packed::new)
		);
	}
}
