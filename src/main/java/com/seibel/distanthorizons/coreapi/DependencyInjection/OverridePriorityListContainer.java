package com.seibel.distanthorizons.coreapi.DependencyInjection;

import com.seibel.distanthorizons.api.interfaces.override.IDhApiOverrideable;
import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

import java.util.ArrayList;

/**
 * Contains a list of overrides and their priorities.
 *
 * @author James Seibel
 * @version 2022-9-5
 */
public class OverridePriorityListContainer implements IBindable
{
	/** Sorted highest priority to lowest */
	private final ArrayList<OverridePriorityPair> overridePairList = new ArrayList<>();
	
	
	/** Doesn't do any validation */
	public void addOverride(IDhApiOverrideable override)
	{
		OverridePriorityPair priorityPair = new OverridePriorityPair(override, override.getPriority());
		this.overridePairList.add(priorityPair);
		
		this.sortList();
	}
	
	/** @return true if the override was removed from the list, false otherwise. */
	public boolean removeOverride(IDhApiOverrideable override)
	{
		boolean overrideRemoved = this.overridePairList.removeIf((pair) -> pair.override.equals(override));
		return overrideRemoved;
	}
	
	
	// getters //
	
	public IDhApiOverrideable getOverrideWithLowestPriority()
	{
		if (this.overridePairList.size() == 0)
		{
			return null;
		}
		else
		{
			// last item should have the highest priority
			return this.overridePairList.get(this.overridePairList.size() - 1).override;
		}
	}
	public IDhApiOverrideable getOverrideWithHighestPriority()
	{
		if (this.overridePairList.size() != 0)
		{
			return this.overridePairList.get(0).override;
		}
		else
		{
			return null;
		}
	}
	public IDhApiOverrideable getCoreOverride()
	{
		int lastIndex = this.overridePairList.size() - 1;
		if (this.overridePairList.get(lastIndex) != null && this.overridePairList.get(lastIndex).priority == OverrideInjector.CORE_PRIORITY)
		{
			return this.overridePairList.get(lastIndex).override;
		}
		else
		{
			return null;
		}
	}
	/** Returns null if no override with the given priority is found */
	public IDhApiOverrideable getOverrideWithPriority(int priority)
	{
		for (OverridePriorityPair pair : this.overridePairList)
		{
			if (pair.priority == priority)
			{
				return pair.override;
			}
		}
		
		return null;
	}
	
	
	// utils //
	
	/** sort the list so the highest priority item is first in the list */
	private void sortList() { this.overridePairList.sort((x, y) -> Integer.compare(y.priority, x.priority)); }
	
	
	
	private class OverridePriorityPair
	{
		public final IDhApiOverrideable override;
		public int priority;
		
		public OverridePriorityPair(IDhApiOverrideable newOverride, int newPriority)
		{
			this.override = newOverride;
			this.priority = newPriority;
		}
		
	}
	
}
