package net.minecraft.worldedit.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.clipboard.Clipboard;
import net.minecraft.worldedit.core.EditSession;
import net.minecraft.worldedit.core.WorldEdit;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.math.transform.AffineTransform;
import net.minecraft.worldedit.platform.MattMCPlatform;
import net.minecraft.worldedit.region.Region;
import net.minecraft.worldedit.region.selector.CuboidRegionSelector;
import net.minecraft.worldedit.session.LocalSession;
import java.util.Map;
import java.util.Set;

/**
 * Clipboard-related commands for WorldEdit.
 * Includes //copy, //cut, //paste, //rotate, //flip.
 */
public class ClipboardCommands {
    private static final Set<Character> COPY_CUT_SWITCHES = Set.of('e', 'b');
    private static final Set<Character> PASTE_SWITCHES = Set.of('a', 'v', 'o', 's', 'n', 'e', 'b');
    
    /**
     * Register all clipboard commands.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // //copy command
        dispatcher.register(Commands.literal("/copy")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.copy"))
            .executes(ctx -> copy(ctx, ""))
            .then(Commands.argument("options", StringArgumentType.greedyString())
                .executes(ctx -> copy(ctx, StringArgumentType.getString(ctx, "options")))));
        
        // //cut command
        dispatcher.register(Commands.literal("/cut")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.cut"))
            .executes(ctx -> cut(ctx, "air", ""))
            .then(Commands.argument("arg1", StringArgumentType.word())
                .executes(ctx -> {
                    String arg1 = StringArgumentType.getString(ctx, "arg1");
                    if (arg1.startsWith("-")) {
                        return cut(ctx, "air", arg1);
                    }
                    return cut(ctx, arg1, "");
                })
                .then(Commands.argument("rest", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String arg1 = StringArgumentType.getString(ctx, "arg1");
                        String rest = StringArgumentType.getString(ctx, "rest");
                        if (arg1.startsWith("-")) {
                            return cut(ctx, "air", arg1 + " " + rest);
                        }
                        return cut(ctx, arg1, rest);
                    }))));
        
        // //paste command
        dispatcher.register(Commands.literal("/paste")
            .requires(source -> source.isPlayer() && hasPermission(source, "worldedit.clipboard.paste"))
            .executes(ctx -> paste(ctx, ""))
            .then(Commands.argument("options", StringArgumentType.greedyString())
                .executes(ctx -> paste(ctx, StringArgumentType.getString(ctx, "options")))));
        
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
    private static int copy(CommandContext<CommandSourceStack> context, String optionsTail) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        ParsedOptions options = parseOptions(optionsTail, COPY_CUT_SWITCHES, player);
        if (options == null) {
            return 0;
        }

        if (!options.positionals().isEmpty()) {
            player.sendSystemMessage(Component.literal("§cUnexpected argument(s): " + String.join(" ", options.positionals())));
            return 0;
        }

        if (options.switches().contains('e') || options.switches().contains('b')) {
            player.sendSystemMessage(Component.literal("§eSwitches -e/-b are accepted, but entities/biomes are not yet persisted in this clipboard implementation."));
        }
        
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
    private static int cut(CommandContext<CommandSourceStack> context, String leavePattern, String optionsTail) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        ParsedOptions options = parseOptions(optionsTail, COPY_CUT_SWITCHES, player);
        if (options == null) {
            return 0;
        }

        if (!options.positionals().isEmpty()) {
            player.sendSystemMessage(Component.literal("§cUnexpected argument(s): " + String.join(" ", options.positionals())));
            return 0;
        }

        if (options.switches().contains('e') || options.switches().contains('b')) {
            player.sendSystemMessage(Component.literal("§eSwitches -e/-b are accepted, but entities/biomes are not yet persisted in this clipboard implementation."));
        }
        
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
        EditSession editSession = session.createEditSession(world);

        BlockState leaveState = parseBlockState(leavePattern);
        if (leaveState == null) {
            player.sendSystemMessage(Component.literal("§cInvalid block for cut replacement: " + leavePattern));
            return 0;
        }
        
        // Copy and remove blocks
        for (BlockVector3 pos : region) {
            BlockState block = world.getBlockState(pos.toBlockPos());
            clipboard.setBlock(pos, block);
            editSession.setBlock(pos, leaveState);
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
    private static int paste(CommandContext<CommandSourceStack> context, String optionsTail) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel world = player.level();
        ParsedOptions options = parseOptions(optionsTail, PASTE_SWITCHES, player);
        if (options == null) {
            return 0;
        }

        if (!options.positionals().isEmpty()) {
            player.sendSystemMessage(Component.literal("§cUnexpected argument(s): " + String.join(" ", options.positionals())));
            return 0;
        }

        boolean ignoreAirBlocks = options.switches().contains('a');
        boolean pasteAtOrigin = options.switches().contains('o');
        boolean selectOnly = options.switches().contains('n');
        boolean selectAfter = selectOnly || options.switches().contains('s');

        if (options.switches().contains('v') || options.switches().contains('e') || options.switches().contains('b')) {
            player.sendSystemMessage(Component.literal("§eSwitches -v/-e/-b are accepted, but this clipboard currently stores blocks only."));
        }
        
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(player);
        
        if (!session.hasClipboard()) {
            player.sendSystemMessage(Component.literal("Clipboard is empty"));
            return 0;
        }
        
        Clipboard clipboard = (Clipboard) session.getClipboard();
        
        System.out.println("Pasting clipboard with " + clipboard.getVolume() + " blocks");
        System.out.println("Clipboard blocks map size: " + clipboard.getBlocks().size());
        
        BlockVector3 target = pasteAtOrigin ? clipboard.getOrigin() : BlockVector3.from(player.blockPosition());
        BlockVector3 origin = clipboard.getOrigin();
        AffineTransform transform = clipboard.getTransform();

        if (selectAfter) {
            BlockVector3 selectionMin = null;
            BlockVector3 selectionMax = null;

            for (BlockVector3 clipboardPos : clipboard.getBlocks().keySet()) {
                BlockVector3 relativePos = clipboardPos.subtract(origin);
                if (transform != null) {
                    relativePos = transform.apply(relativePos);
                }
                BlockVector3 worldPos = target.add(relativePos);

                if (selectionMin == null) {
                    selectionMin = worldPos;
                    selectionMax = worldPos;
                } else {
                    selectionMin = BlockVector3.at(
                        Math.min(selectionMin.getX(), worldPos.getX()),
                        Math.min(selectionMin.getY(), worldPos.getY()),
                        Math.min(selectionMin.getZ(), worldPos.getZ())
                    );
                    selectionMax = BlockVector3.at(
                        Math.max(selectionMax.getX(), worldPos.getX()),
                        Math.max(selectionMax.getY(), worldPos.getY()),
                        Math.max(selectionMax.getZ(), worldPos.getZ())
                    );
                }
            }

            if (selectionMin != null && selectionMax != null) {
                session.setRegionSelector(world, new CuboidRegionSelector(world, selectionMin, selectionMax));
                player.sendSystemMessage(Component.literal("§aSelection updated to pasted region"));
            }
        }

        if (selectOnly) {
            player.sendSystemMessage(Component.literal("§aClipboard region selected (-n), no blocks pasted"));
            return Command.SINGLE_SUCCESS;
        }

        // Create edit session for pasting
        EditSession editSession = session.createEditSession(world);
        
        int count = 0;
        for (Map.Entry<BlockVector3, BlockState> entry : clipboard.getBlocks().entrySet()) {
            BlockVector3 clipboardPos = entry.getKey();
            BlockState block = entry.getValue();

            if (ignoreAirBlocks && block.is(Blocks.AIR)) {
                continue;
            }
            
            // Calculate relative position from origin
            BlockVector3 relativePos = clipboardPos.subtract(origin);
            
            // Apply transformation if set (for rotate/flip)
            if (transform != null) {
                relativePos = transform.apply(relativePos);
            }
            
            // Calculate final paste position: target + transformedRelativePos
            // This makes the clipboard paste relative to where the player is standing
            BlockVector3 pastePos = target.add(relativePos);
            
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
        // Create new rotation transform
        AffineTransform newRotation = AffineTransform.rotateY(normalizedDegrees);
        
        // Combine with existing transform (if any) to allow stacking rotations
        AffineTransform existingTransform = clipboard.getTransform();
        if (existingTransform != null) {
            clipboard.setTransform(existingTransform.combine(newRotation));
        } else {
            clipboard.setTransform(newRotation);
        }
        
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
        AffineTransform newFlip;
        float yaw = player.getYRot();
        
        // Determine flip axis based on player's facing direction
        if ((yaw >= -45 && yaw < 45) || (yaw >= 135 || yaw < -135)) {
            // Facing north/south, flip along Z axis
            newFlip = AffineTransform.flipZ();
        } else {
            // Facing east/west, flip along X axis
            newFlip = AffineTransform.flipX();
        }
        
        // Combine with existing transform (if any) to allow stacking transforms
        AffineTransform existingTransform = clipboard.getTransform();
        if (existingTransform != null) {
            clipboard.setTransform(existingTransform.combine(newFlip));
        } else {
            clipboard.setTransform(newFlip);
        }
        
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

    private static BlockState parseBlockState(String blockName) {
        ResourceLocation blockId = blockName.contains(":")
            ? ResourceLocation.tryParse(blockName)
            : ResourceLocation.withDefaultNamespace(blockName);
        if (blockId == null) {
            return null;
        }

        Block block = BuiltInRegistries.BLOCK.getValue(blockId);
        return block == null ? null : block.defaultBlockState();
    }

    private static ParsedOptions parseOptions(String tail, Set<Character> allowedSwitches, ServerPlayer player) {
        Set<Character> switches = new HashSet<>();
        List<String> positionals = new ArrayList<>();

        if (!tail.isBlank()) {
            for (String token : tail.trim().split("\\s+")) {
                if (token.startsWith("-") && token.length() > 1) {
                    for (int i = 1; i < token.length(); i++) {
                        char option = token.charAt(i);
                        if (!allowedSwitches.contains(option)) {
                            player.sendSystemMessage(Component.literal("§cUnknown switch '-" + option + "'."));
                            return null;
                        }
                        switches.add(option);
                    }
                } else {
                    positionals.add(token);
                }
            }
        }

        return new ParsedOptions(switches, positionals);
    }

    private record ParsedOptions(Set<Character> switches, List<String> positionals) {
    }
}
