package com.seibel.distanthorizons.core.util.objects.quadTree.iterators;

import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

public class QuadNodeDirectChildIterator<T> implements Iterator<QuadNode<T>>
{
	private final QuadNodeChildIndexIterator<T> childIndexIterator;
	private final QuadNode<T> parentNode;
	
	
	public QuadNodeDirectChildIterator(QuadNode<T> parentNode)
	{
		this.parentNode = parentNode;
		this.childIndexIterator = new QuadNodeChildIndexIterator<>(this.parentNode, false);
	}
	
	
	
	@Override
	public boolean hasNext() { return this.childIndexIterator.hasNext(); }
	
	@Override
	public QuadNode<T> next()
	{
		if (!this.hasNext())
		{
			throw new NoSuchElementException();
		}
		
		
		int childIndex = this.childIndexIterator.next();
		QuadNode<T> node = this.parentNode.getChildByIndex(childIndex);
		return node;
	}
	
	
	/** Unimplemented */
	@Override
	public void remove() { throw new UnsupportedOperationException("remove"); }
	
	@Override
	public void forEachRemaining(Consumer<? super QuadNode<T>> action) { Iterator.super.forEachRemaining(action); }
	
}