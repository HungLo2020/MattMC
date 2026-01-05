package net.minecraft.worldedit.session;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.worldedit.region.Region;
import net.minecraft.worldedit.region.RegionSelector;
import net.minecraft.worldedit.region.selector.CuboidRegionSelector;
import net.minecraft.worldedit.tool.Tool;
import net.minecraft.world.item.Item;
import java.util.*;

/**
 * Stores session information for a single player.
 * This includes selections, clipboard, tools, history, and preferences.
 */
public class LocalSession {
    // Selection state per world
    private final Map<ServerLevel, RegionSelector> selectors;
    private RegionSelector currentSelector;
    
    // Tool bindings
    private final Map<Item, Tool> toolBindings;
    
    // History
    private final Deque<Object> history; // TODO: Change to EditSession when implemented
    private int historyPointer;
    private final int maxHistorySize;
    
    // Preferences
    private boolean fastMode;
    private boolean wandItem;
    private int defaultChangeLimit;
    
    // Clipboard
    private Object clipboard; // TODO: Change to ClipboardHolder when implemented
    
    public LocalSession() {
        this.selectors = new HashMap<>();
        this.toolBindings = new HashMap<>();
        this.history = new LinkedList<>();
        this.historyPointer = 0;
        this.maxHistorySize = 15;
        this.fastMode = false;
        this.wandItem = true;
        this.defaultChangeLimit = 1000000;
    }
    
    /**
     * Get the region selector for a world.
     */
    public RegionSelector getRegionSelector(ServerLevel world) {
        return selectors.computeIfAbsent(world, w -> {
            CuboidRegionSelector selector = new CuboidRegionSelector(world);
            currentSelector = selector;
            return selector;
        });
    }
    
    /**
     * Set the region selector for a world.
     */
    public void setRegionSelector(ServerLevel world, RegionSelector selector) {
        selectors.put(world, selector);
        currentSelector = selector;
    }
    
    /**
     * Get the current region selector.
     */
    public RegionSelector getCurrentSelector() {
        return currentSelector;
    }
    
    /**
     * Get the selected region, or null if incomplete.
     */
    public Region getSelection(ServerLevel world) {
        RegionSelector selector = getRegionSelector(world);
        try {
            return selector.getRegion();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check if a selection is defined.
     */
    public boolean isSelectionDefined(ServerLevel world) {
        return getSelection(world) != null;
    }
    
    /**
     * Bind a tool to an item.
     */
    public void setTool(Item item, Tool tool) {
        if (tool == null) {
            toolBindings.remove(item);
        } else {
            toolBindings.put(item, tool);
        }
    }
    
    /**
     * Get the tool bound to an item.
     */
    public Tool getTool(Item item) {
        return toolBindings.get(item);
    }
    
    /**
     * Check if an item has a tool bound.
     */
    public boolean hasTool(Item item) {
        return toolBindings.containsKey(item);
    }
    
    /**
     * Get fast mode setting.
     */
    public boolean isFastMode() {
        return fastMode;
    }
    
    /**
     * Set fast mode.
     */
    public void setFastMode(boolean fastMode) {
        this.fastMode = fastMode;
    }
    
    /**
     * Check if player has the wand item.
     */
    public boolean hasWandItem() {
        return wandItem;
    }
    
    /**
     * Set whether player has the wand item.
     */
    public void setWandItem(boolean wandItem) {
        this.wandItem = wandItem;
    }
    
    /**
     * Get the default change limit.
     */
    public int getDefaultChangeLimit() {
        return defaultChangeLimit;
    }
    
    /**
     * Set the default change limit.
     */
    public void setDefaultChangeLimit(int limit) {
        this.defaultChangeLimit = limit;
    }
    
    /**
     * Get clipboard.
     */
    public Object getClipboard() {
        return clipboard;
    }
    
    /**
     * Set clipboard.
     */
    public void setClipboard(Object clipboard) {
        this.clipboard = clipboard;
    }
    
    /**
     * Check if clipboard is set.
     */
    public boolean hasClipboard() {
        return clipboard != null;
    }
}
