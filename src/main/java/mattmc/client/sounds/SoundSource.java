package mattmc.client.sounds;

/**
 * Sound category enum, mirroring Minecraft's SoundSource.
 * Each category has an independent volume control that scales the final output.
 * <p>
 * Categories:
 * <ul>
 *   <li>MASTER - Master volume, affects all sounds</li>
 *   <li>MUSIC - Background music tracks</li>
 *   <li>RECORDS - Jukebox/music disc sounds</li>
 *   <li>WEATHER - Rain, thunder, etc.</li>
 *   <li>BLOCKS - Block breaking, placing, step sounds</li>
 *   <li>HOSTILE - Hostile mob sounds</li>
 *   <li>NEUTRAL - Neutral mob sounds (animals, villagers)</li>
 *   <li>PLAYERS - Player sounds (footsteps, damage)</li>
 *   <li>AMBIENT - Ambient/environmental sounds</li>
 *   <li>VOICE - Voice chat (reserved for future use)</li>
 * </ul>
 */
public enum SoundSource {
    MASTER("master", "Master Volume"),
    MUSIC("music", "Music"),
    RECORDS("record", "Jukebox/Note Blocks"),
    WEATHER("weather", "Weather"),
    BLOCKS("block", "Blocks"),
    HOSTILE("hostile", "Hostile Creatures"),
    NEUTRAL("neutral", "Friendly Creatures"),
    PLAYERS("player", "Players"),
    AMBIENT("ambient", "Ambient/Environment"),
    VOICE("voice", "Voice/Speech");

    private final String name;
    private final String displayName;

    SoundSource(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    /**
     * Get the internal name used for serialization/options.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the user-facing display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Find a SoundSource by its internal name.
     * @param name The internal name to search for
     * @return The matching SoundSource, or null if not found
     */
    public static SoundSource byName(String name) {
        for (SoundSource source : values()) {
            if (source.name.equals(name)) {
                return source;
            }
        }
        return null;
    }
}
