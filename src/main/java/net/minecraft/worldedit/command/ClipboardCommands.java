package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.clipboard.Clipboard;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.region.Region;
import net.minecraft.worldedit.session.LocalSession;
import java.util.Map;

/**
 * Clipboard-related commands for WorldEdit.
 * Includes //copy, //cut, //paste, //rotate, //flip.
 */
public class ClipboardCommands {
    
    /**
     * Register all clipboard commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //copy command
        dispatcher.register(Commands.literal("/copy")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.copy"))
            .executes(ClipboardCommands::copy));
        
        // //cut command
        dispatcher.register(Commands.literal("/cut")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.cut"))
            .executes(ClipboardCommands::cut));
        
        // //paste command
        dispatcher.register(Commands.literal("/paste")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.paste"))
            .executes(ClipboardCommands::paste));
        
        // //rotate command
        dispatcher.register(Commands.literal("/rotate")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.rotate"))
            .then(Commands.argument("degrees", IntegerArgumentType.integer())
                .executes(ctx -> rotate(ctx, IntegerArgumentType.getInteger(ctx, "degrees")))));
        
        // //flip command  
        dispatcher.register(Commands.literal("/flip")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.flip"))
            .executes(ClipboardCommands::flip));
    }
    
    /**
     * Copy the selection to clipboard.
     */
    private static int copy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Region region = session.getSelection(world);
        
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        // Create clipboard
        BlockVector3 origin = BlockVector3.from(player.blockPosition());
        Clipboard clipboard = new Clipboard(region, origin);
        
        // Copy blocks
        for (BlockVector3 pos : region) {
            BlockState block = world.getBlockState(pos.toBlockPos());
            clipboard.setBlock(pos, block);
        }
        
        // Store in session
        session.setClipboard(clipboard);
        
        player.sendSystemMessage(Component.literal(
            String.format("Copied %d blocks to clipboard", clipboard.getVolume())
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Cut the selection to clipboard (copy then delete).
     */
    private static int cut(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        Region region = session.getSelection(world);
        
        if (region == null) {
            player.sendSystemMessage(Component.literal("No selection defined"));
            return 0;
        }
        
        // Create clipboard
        BlockVector3 origin = BlockVector3.from(player.blockPosition());
        Clipboard clipboard = new Clipboard(region, origin);
        
        // Create edit session for cutting
        EditSession editSession = new EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        // Copy and remove blocks
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockVector3 pos : region) {
            BlockState block = world.getBlockState(pos.toBlockPos());
            clipboard.setBlock(pos, block);
            editSession.setBlock(pos, air);
        }
        
        // Store in session and remember for undo
        session.setClipboard(clipboard);
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Cut %d blocks to clipboard", clipboard.getVolume())
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Paste the clipboard.
     */
    private static int paste(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        if (!session.hasClipboard()) {
            player.sendSystemMessage(Component.literal("Clipboard is empty"));
            return 0;
        }
        
        Clipboard clipboard = (Clipboard) session.getClipboard();
        
        System.out.println("Pasting clipboard with " + clipboard.getVolume() + " blocks");
        System.out.println("Clipboard blocks map size: " + clipboard.getBlocks().size());
        
        // Create edit session for pasting
        EditSession editSession = new EditSession(world, session.getDefaultChangeLimit());
        editSession.setFastMode(session.isFastMode());
        
        // Paste blocks
        BlockVector3 target = BlockVector3.from(player.blockPosition());
        BlockVector3 origin = clipboard.getOrigin();
        
        int count = 0;
        for (Map.Entry<BlockVector3, BlockState> entry : clipboard.getBlocks().entrySet()) {
            BlockVector3 clipboardPos = entry.getKey();
            BlockState block = entry.getValue();
            
            // Calculate paste position: target + (clipboardPos - origin)
            // This makes the clipboard paste relative to where the player is standing
            BlockVector3 pastePos = target.add(clipboardPos.subtract(origin));
            
            if (editSession.setBlock(pastePos, block)) {
                count++;
            }
        }
        
        System.out.println("Pasted " + count + " blocks");
        
        // Remember for undo
        session.remember(editSession);
        
        player.sendSystemMessage(Component.literal(
            String.format("Pasted %d blocks", count)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Rotate the clipboard.
     */
    private static int rotate(CommandContext<CommandSourceStack> context, int degrees) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        if (!session.hasClipboard()) {
            player.sendSystemMessage(Component.literal("§cNo clipboard available. Use //copy first."));
            return 0;
        }
        
        Clipboard clipboard = (Clipboard) session.getClipboard();
        
        // Normalize degrees to 0, 90, 180, 270
        int normalizedDegrees = ((degrees % 360) + 360) % 360;
        if (normalizedDegrees % 90 != 0) {
            player.sendSystemMessage(Component.literal("§cDegrees must be a multiple of 90"));
            return 0;
        }
        
        // Apply rotation transform to clipboard
        net.minecraft.worldedit.math.transform.AffineTransform transform = 
            net.minecraft.worldedit.math.transform.AffineTransform.rotateY(normalizedDegrees);
        clipboard.setTransform(transform);
        
        player.sendSystemMessage(Component.literal(
            String.format("§aClipboard rotated %d degrees", normalizedDegrees)
        ));
        
        return Command.SINGLE_SUCCESS;
    }
    
    /**
     * Flip the clipboard.
     */
    private static int flip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        if (!session.hasClipboard()) {
            player.sendSystemMessage(Component.literal("§cNo clipboard available. Use //copy first."));
            return 0;
        }
        
        Clipboard clipboard = (Clipboard) session.getClipboard();
        
        // Apply flip transform based on player's facing direction
        net.minecraft.worldedit.math.transform.AffineTransform transform;
        float yaw = player.getYRot();
        
        // Determine flip axis based on player's facing direction
        if ((yaw >= -45 && yaw < 45) || (yaw >= 135 || yaw < -135)) {
            // Facing north/south, flip along Z axis
            transform = net.minecraft.worldedit.math.transform.AffineTransform.flipZ();
        } else {
            // Facing east/west, flip along X axis
            transform = net.minecraft.worldedit.math.transform.AffineTransform.flipX();
        }
        
        clipboard.setTransform(transform);
        
        player.sendSystemMessage(Component.literal("§aClipboard flipped"));
        
        return Command.SINGLE_SUCCESS;
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
