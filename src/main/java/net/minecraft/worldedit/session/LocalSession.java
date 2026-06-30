package net.minecraft.worldedit.session;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.mask.Mask;
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
    
    // History - stores completed EditSessions for undo/redo
    private final List<EditSession> history;
    private int historyPointer;
    private final int maxHistorySize;
    
    // Preferences
    private boolean fastMode;
    private boolean wandItem;
    private int defaultChangeLimit;
    private Mask mask;
    
    // Clipboard
    private Object clipboard; // TODO: Change to ClipboardHolder when implemented
    
    public LocalSession() {
        this.selectors = new HashMap<>();
        this.toolBindings = new HashMap<>();
        this.history = new ArrayList<>();
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
     * Get the global mask applied to new edit sessions.
     */
    public Mask getMask() {
        return mask;
    }

    /**
     * Set the global mask applied to new edit sessions.
     */
    public void setMask(Mask mask) {
        this.mask = mask;
    }

    /**
     * Create an edit session with this player's current preferences.
     */
    public EditSession createEditSession(ServerLevel world) {
        EditSession editSession = new EditSession(world, defaultChangeLimit);
        editSession.setFastMode(fastMode);
        editSession.setMask(mask);
        return editSession;
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
    
    /**
     * Remember an edit session for undo/redo.
     */
    public void remember(EditSession editSession) {
        // Remove any redo history
        while (history.size() > historyPointer) {
            history.remove(history.size() - 1);
        }
        
        // Add new session
        history.add(editSession);
        historyPointer++;
        
        // Enforce size limit
        while (history.size() > maxHistorySize) {
            history.remove(0);
            historyPointer--;
        }
    }
    
    /**
     * Undo the last edit.
     * @return the edit session that was undone, or null if nothing to undo
     */
    public EditSession undo() {
        if (historyPointer > 0) {
            historyPointer--;
            EditSession session = history.get(historyPointer);
            session.undo();
            return session;
        }
        return null;
    }
    
    /**
     * Redo the last undone edit.
     * @return the edit session that was redone, or null if nothing to redo
     */
    public EditSession redo() {
        if (historyPointer < history.size()) {
            EditSession session = history.get(historyPointer);
            session.redo();
            historyPointer++;
            return session;
        }
        return null;
    }
    
    /**
     * Clear the edit history.
     */
    public void clearHistory() {
        history.clear();
        historyPointer = 0;
    }
    
    /**
     * Get the number of edits that can be undone.
     */
    public int getUndoCount() {
        return historyPointer;
    }
    
    /**
     * Get the number of edits that can be redone.
     */
    public int getRedoCount() {
        return history.size() - historyPointer;
    }
}
