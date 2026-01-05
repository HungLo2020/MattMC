package net.minecraft.worldedit.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Registers all WorldEdit commands with the server.
 */
public class WorldEditCommands {
    
    /**
     * Register all WorldEdit commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Selection commands
        SelectionCommands.register(dispatcher);
        
        // Region manipulation commands
        RegionCommands.register(dispatcher);
        
        // History commands
        HistoryCommands.register(dispatcher);
        
        // Future: Add more command groups as they're implemented
        // ClipboardCommands.register(dispatcher);
        // GenerationCommands.register(dispatcher);
        // UtilityCommands.register(dispatcher);
        // ToolCommands.register(dispatcher);
        // BrushCommands.register(dispatcher);
        // NavigationCommands.register(dispatcher);
        // SchematicCommands.register(dispatcher);
    }
}
