package com.seibel.distanthorizons.common.wrappers.gui;

import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import net.distant_horizons.coreapi.ModInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import net.minecraft.resources.ResourceLocation;


public class DhDebugScreenEntry implements net.minecraft.client.gui.components.debug.DebugScreenEntry
{
	public static void register()
	{
		// This method is private, so its access will need to be widened
		DebugScreenEntries.register(
				// The id, this will be displayed on the options screen
				ResourceLocation.fromNamespaceAndPath(ModInfo.RESOURCE_NAMESPACE, "distant_horizons"),
				
				// The screen entry
				new DhDebugScreenEntry()
		);
	}
	
	
	@Override
	public void display(@NotNull DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk)
	{
		List<String> messageList = new ArrayList<>();
		F3Screen.addStringToDisplay(messageList);
		
		for (String message : messageList)
		{
			displayer.addLine(message);
		}
		
		//// The following will display like so if it is the only entry on the screen:
		//// First left!                                                First Right!
		//// 
		//// Hello world!                                               Random text!
		//// Lorem ipsum.
		////                                                            I am another group!
		//// I am one group                                             This will appear after with no line breaks!
		//// All in a row
		//// Provided in a list.
		////
		//
		//displayer.addLine("Hello world!");
		//displayer.addLine("Lorem ipsum.");
		//displayer.addLine("Random text!");
		//
		//// These will be displayed first
		//displayer.addPriorityLine("First left!");
		//displayer.addPriorityLine("First right!");
		//
		//// These will be grouped separately based on the key
		//displayer.addToGroup(GROUP_ONE, List.of(
		//		"I am one group",
		//		"All in a row",
		//		"Provided in a list."
		//));
		//
		//displayer.addToGroup(GROUP_TWO, "I am another group!");
		//displayer.addToGroup(GROUP_TWO, "This will appear after with no line breaks!");
	}
	
	@Override
	public boolean isAllowed(boolean reducedDebugInfo)
	{
		// Always show regardless of accessibility option
		return true;
	}
}
