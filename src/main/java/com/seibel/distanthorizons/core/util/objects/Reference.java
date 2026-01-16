package com.seibel.distanthorizons.core.util.objects;

public class Reference<T>
{
	public T value;
	
	
	public Reference() { }
	
	public Reference(T value) { this.value = value; }
	
	public T swap(T v)
	{
		T old = this.value;
		this.value = v;
		return old;
	}
	
	public boolean isEmpty() { return value == null; }
	
}
