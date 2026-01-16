package com.seibel.distanthorizons.core.util.objects;

import java.util.Objects;

/** A simple way to hold 2 objects together */
public final class Pair<T, U>
{
	public final T first;
	public final U second;
	
	public Pair(T first, U second)
	{
		this.second = second;
		this.first = first;
	}
	
	@Override
	public String toString() { return "(" + this.first + ", " + this.second + ")"; }
	
	@Override
	public int hashCode() { return Objects.hash(this.first, this.second); }
	
}
