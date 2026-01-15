package net.distanthorizons.core.network.exceptions;

/** Fired if the current section is not fully generated and underlying generator does not support N-sized generation. */
public class SectionRequiresSplittingException extends Exception
{
	public SectionRequiresSplittingException() { this("Section requires splitting"); }
	
	public SectionRequiresSplittingException(String message) { super(message); }
	
}
