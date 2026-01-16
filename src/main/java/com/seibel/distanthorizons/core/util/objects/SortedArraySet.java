package com.seibel.distanthorizons.core.util.objects;

import java.util.*;

public class SortedArraySet<E>
{
	private final ArrayList<E> list = new ArrayList<>();
	private final HashSet<E> set = new HashSet<>();
	
	private final Comparator<? super E> comparator;
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public SortedArraySet(Comparator<? super E> comparator)
	{
		this.comparator = comparator;
	}
	
	public SortedArraySet(Collection<? extends E> collection, Comparator<? super E> comparator)
	{
		this.comparator = comparator;
		this.list.addAll(collection);
		this.list.sort(comparator);
	}
	
	
	
	//==============//
	// list methods //
	//==============//
	
	public void add(E element)
	{
		if (this.set.add(element))
		{
			this.list.add(element);
		}
	}
	
	public E get(int index) { return this.list.get(index); }
	
	public int size() { return this.list.size(); }
	
	public void clear()
	{
		this.list.clear();
		this.set.clear();
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString()
	{
		return "SortedArraySet{" +
				"list=" + this.list +
				", comparator=" + this.comparator +
				'}';
	}
	
	
	
}
