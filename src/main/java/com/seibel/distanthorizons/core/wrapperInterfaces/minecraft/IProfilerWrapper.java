package com.seibel.distanthorizons.core.wrapperInterfaces.minecraft;

import com.seibel.distanthorizons.coreapi.interfaces.dependencyInjection.IBindable;

/**
 * @author James Seibel
 * @version 11-20-2021
 */
public interface IProfilerWrapper extends IBindable
{
	// Note to self:
	// if "unspecified" shows up in the pie chart, it is
	// possibly because the amount of time between sections
	// is too small for the profiler to measures
	void push(String newSection);
	
	void popPush(String newSection);
	
	void pop();
	
}
