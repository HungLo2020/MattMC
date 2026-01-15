package net.distanthorizons.common.wrappers.minecraft;

import net.distanthorizons.core.wrapperInterfaces.minecraft.IProfilerWrapper;

import net.minecraft.util.profiling.ProfilerFiller;

/**
 * @author James Seibel
 * @version 11-20-2021
 */
public class ProfilerWrapper implements IProfilerWrapper
{
	public ProfilerFiller profiler;
	
	public ProfilerWrapper(ProfilerFiller newProfiler) { this.profiler = newProfiler; }
	
	
	/** starts a new section inside the currently running section */
	@Override
	public void push(String newSection) { this.profiler.push(newSection); }
	
	/** ends the currently running section and starts a new one */
	@Override
	public void popPush(String newSection) { this.profiler.popPush(newSection); }
	
	/** ends the currently running section */
	@Override
	public void pop() { this.profiler.pop(); }
	
}
