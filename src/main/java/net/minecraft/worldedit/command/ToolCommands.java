package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.session.LocalSession;
import net.minecraft.worldedit.tool.SuperPickaxeTool;
import net.minecraft.worldedit.tool.BrushTool;

/**
 * Tool-related commands for WorldEdit.
 * Includes /tool, //tool, /none, //none, //, brush commands, etc.
 */
public class ToolCommands {
    
    /**
     * Register all tool commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //tool command (unbind tool)
        dispatcher.register(Commands.literal("/tool")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.tool.none"))
            .then(Commands.literal("/none")
                .executes(ToolCommands::toolNone)));
        
        // //none command (unbind tool)
        dispatcher.register(Commands.literal("/none")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.tool.none"))
            .executes(ToolCommands::toolNone));
        
        // // command (super pickaxe toggle)
        dispatcher.register(Commands.literal("/")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.superpickaxe"))
            .executes(ToolCommands::superPickaxeToggle));
        
        // //superpickaxe command
        dispatcher.register(Commands.literal("/superpickaxe")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.superpickaxe"))
            .then(Commands.literal("/single")
                .executes(ctx -> superPickaxeMode(ctx, "single")))
            .then(Commands.literal("/area")
                .then(Commands.argument("range", IntegerArgumentType.integer(1, 5))
                    .executes(ctx -> superPickaxeMode(ctx, "area"))))
            .then(Commands.literal("/recursive")
                .then(Commands.argument("range", IntegerArgumentType.integer(1, 5))
                    .executes(ctx -> superPickaxeMode(ctx, "recursive")))));
        
        // //brush sphere command
        dispatcher.register(Commands.literal("/brush")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.brush.sphere"))
            .then(Commands.literal("/sphere")
                .then(Commands.argument("block", StringArgumentType.word())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> brushSphere(ctx,
                            StringArgumentType.getString(ctx, "block"),
                            IntegerArgumentType.getInteger(ctx, "radius"))))))
            .then(Commands.literal("/cylinder")
                .then(Commands.argument("block", StringArgumentType.word())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> brushCylinder(ctx,
                            StringArgumentType.getString(ctx, "block"),
                            IntegerArgumentType.getInteger(ctx, "radius"))))))
            .then(Commands.literal("/smooth")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10))
                    .executes(ctx -> brushSmooth(ctx,
                        IntegerArgumentType.getInteger(ctx, "radius"))))));
    }
    
    /**
     * Unbind the current tool.
     */
    private static int toolNone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to unbind"));
            return 0;
        }
        
        session.setTool(item.getItem(), null);
        player.sendSystemMessage(Component.literal("Tool unbound from " + item.getHoverName().getString()));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Toggle super pickaxe.
     */
    private static int superPickaxeToggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind super pickaxe"));
            return 0;
        }
        
        if (session.hasTool(item.getItem())) {
            // Toggle off
            session.setTool(item.getItem(), null);
            player.sendSystemMessage(Component.literal("§eSuper pickaxe disabled"));
        } else {
            // Toggle on
            SuperPickaxeTool tool = new SuperPickaxeTool("single", 1);
            session.setTool(item.getItem(), tool);
            player.sendSystemMessage(Component.literal("§aSuper pickaxe enabled (single mode)"));
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Set super pickaxe mode.
     */
    private static int superPickaxeMode(CommandContext<CommandSourceStack> context, String mode) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind super pickaxe"));
            return 0;
        }
        
        int range = 1;
        if (!mode.equals("single")) {
            try {
                range = IntegerArgumentType.getInteger(context, "range");
            } catch (IllegalArgumentException e) {
                range = 3;
            }
        }
        
        SuperPickaxeTool tool = new SuperPickaxeTool(mode, range);
        session.setTool(item.getItem(), tool);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aSuper pickaxe enabled (%s mode, range: %d)", mode, range)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Bind sphere brush to item.
     */
    private static int brushSphere(CommandContext<CommandSourceStack> context, String blockName, int radius) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind brush"));
            return 0;
        }
        
        // Parse block
        ResourceLocation blockId = parseBlockId(blockName);
        if (blockId == null) {
            player.sendSystemMessage(Component.literal("Invalid block: " + blockName));
            return 0;
        }
        
        Block block = BuiltInRegistries.BLOCK.getValue(blockId);
        if (block == null) {
            player.sendSystemMessage(Component.literal("Unknown block: " + blockName));
            return 0;
        }
        
        BlockState state = block.defaultBlockState();
        
        // Create and bind brush
        BrushTool brush = new BrushTool("sphere", state, radius);
        session.setTool(item.getItem(), brush);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aSphere brush bound to %s (radius: %d, block: %s)", 
                item.getHoverName().getString(), radius, blockName)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Bind cylinder brush to item.
     */
    private static int brushCylinder(CommandContext<CommandSourceStack> context, String blockName, int radius) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind brush"));
            return 0;
        }
        
        // Parse block
        ResourceLocation blockId = parseBlockId(blockName);
        if (blockId == null) {
            player.sendSystemMessage(Component.literal("Invalid block: " + blockName));
            return 0;
        }
        
        Block block = BuiltInRegistries.BLOCK.getValue(blockId);
        if (block == null) {
            player.sendSystemMessage(Component.literal("Unknown block: " + blockName));
            return 0;
        }
        
        BlockState state = block.defaultBlockState();
        
        // Create and bind brush
        BrushTool brush = new BrushTool("cylinder", state, radius);
        session.setTool(item.getItem(), brush);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aCylinder brush bound to %s (radius: %d, block: %s)", 
                item.getHoverName().getString(), radius, blockName)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Bind smooth brush to item.
     */
    private static int brushSmooth(CommandContext<CommandSourceStack> context, int radius) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        ItemStack item = player.getMainHandItem();
        if (item.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold an item to bind brush"));
            return 0;
        }
        
        // Create and bind brush (smooth doesn't need a block)
        BrushTool brush = new BrushTool("smooth", net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), radius);
        session.setTool(item.getItem(), brush);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aSmooth brush bound to %s (radius: %d)", 
                item.getHoverName().getString(), radius)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Parse a block ID from a string.
     */
    private static ResourceLocation parseBlockId(String name) {
        if (name.contains(":")) {
            return ResourceLocation.tryParse(name);
        } else {
            return ResourceLocation.withDefaultNamespace(name);
        }
    }
    
    /**
     * Check if the source has a permission.
     */
    private static boolean hasPermission(CommandSourceStack source, String permission) {
        if (!WorldEdit.isInitialized()) {
            return false;
        }
        
        try {
            ServerPlayer player = source.getPlayerOrException();
            MattMCPlatform platform = WorldEdit.getInstance().getPlatform();
            return platform.hasPermission(player, permission);
        } catch (CommandSyntaxException e) {
            return false;
        }
    }
}
