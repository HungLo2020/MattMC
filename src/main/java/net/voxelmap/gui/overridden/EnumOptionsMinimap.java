package net.voxelmap.gui.overridden;

public enum EnumOptionsMinimap {
    SHOW_COORDS("voxelmap.options.minimap.showCoordinates", false, true, false),
    HIDE_MINIMAP("voxelmap.options.minimap.hideMinimap", false, true, false),
    CAVE_MODE("voxelmap.options.minimap.caveMode", false, true, false),
    DYNAMIC_LIGHTING("voxelmap.options.minimap.dynamicLighting", false, true, false),
    TERRAIN_DEPTH("voxelmap.options.minimap.terrainDepth", false, false, true),
    SQUARE_MAP("voxelmap.options.minimap.squareMap", false, true, false),
    ROTATES("voxelmap.options.minimap.rotation", false, true, false),
    OLD_NORTH("voxelmap.options.minimap.oldNorth", false, true, false),
    IN_GAME_WAYPOINTS("voxelmap.options.minimap.inGameWaypoints", false, false, true),
    WELCOME_SCREEN("Welcome Screen", false, true, false),
    ZOOM("option.minimapZoom", false, true, false),
    LOCATION("voxelmap.options.minimap.location", false, false, true),
    SIZE("voxelmap.options.minimap.size", false, false, true),
    FILTERING("voxelmap.options.minimap.filtering", false, true, false),
    WATER_TRANSPARENCY("voxelmap.options.minimap.waterTransparency", false, true, false),
    BLOCK_TRANSPARENCY("voxelmap.options.minimap.blockTransparency", false, true, false),
    BIOMES("voxelmap.options.minimap.biomes", false, true, false),
    BIOME_OVERLAY("voxelmap.options.minimap.biomeOverlay", false, false, true),
    CHUNK_GRID("voxelmap.options.minimap.chunkGrid", false, true, false),
    SLIME_CHUNKS("voxelmap.options.minimap.slimeChunks", false, true, false),
    WORLD_BORDER("voxelmap.options.minimap.worldBorder", false, true, false),
    WAYPOINT_DISTANCE("voxelmap.options.minimap.waypoints.distance", true, false, false),
    DEATHPOINTS("voxelmap.options.minimap.waypoints.deathpoints", false, false, true),
    SHOW_WAYPOINTS("voxelmap.options.worldmap.showWaypoints", false, true, false),
    SHOW_WAYPOINT_NAMES("voxelmap.options.worldmap.showWaypointNames", false, true, false),
    MIN_ZOOM("voxelmap.options.worldmap.minZoom", true, false, false),
    MAX_ZOOM("voxelmap.options.worldmap.maxZoom", true, false, false),
    CACHE_SIZE("voxelmap.options.worldmap.cacheSize", true, false, false),
    MOVE_MAP_DOWN_WHILE_STATUS_EFFECT("voxelmap.options.minimap.moveMapBelowStatusEffectIcons", false, true, false),
    MOVE_SCOREBOARD_DOWN("voxelmap.options.minimap.moveScoreboardBelowMap", false, true, false),
    DISTANCE_UNIT_CONVERSION("voxelmap.options.minimap.waypoints.distanceUnitConversion", false, false, true),
    WAYPOINT_SIGN_SCALE("voxelmap.options.minimap.waypoints.waypointSignScale", true, false, false),
    SHOW_IN_GAME_WAYPOINT_NAMES("voxelmap.options.minimap.waypoints.showWaypointNames", false, false, true),
    SHOW_IN_GAME_WAYPOINT_DISTANCES("voxelmap.options.minimap.waypoints.showWaypointDistances", false, false, true);

    private final boolean isFloat;
    private final boolean isBoolean;
    private final boolean isList;
    private final String name;

    EnumOptionsMinimap(String name, boolean isFloat, boolean isBoolean, boolean isList) {
        this.name = name;
        this.isFloat = isFloat;
        this.isBoolean = isBoolean;
        this.isList = isList;
    }

    public boolean isFloat() {
        return this.isFloat;
    }

    public boolean isBoolean() {
        return this.isBoolean;
    }

    public boolean isList() {
        return this.isList;
    }

    public String getName() {
        return this.name;
    }
}
