package com.seibel.distanthorizons.core.util.objects.quadTree.iterators;

import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;

import java.util.*;
import java.util.function.Consumer;

public class QuadNodeChildIndexIterator<T> implements Iterator<Integer>
{
	private final Queue<Integer> iteratorQueue = new ArrayDeque<>();
	
	
	
	public QuadNodeChildIndexIterator(QuadNode<T> parentNode, boolean returnNullChildPos)
	{
		// only get the children if this section isn't at the bottom of the tree
		if (DhSectionPos.getDetailLevel(parentNode.sectionPos) > parentNode.parentTreeLeafDetailLevel)
		{
			// go over each child pos
			for (int i = 0; i < 4; i++)
			{
				// add index to queue if either not null or we want to return null values as well
				if (returnNullChildPos || parentNode.getChildByIndex(i) != null)
				{
					// TODO is it possible that a child could be outside the parent QuadTree's radius?
					this.iteratorQueue.add(i);
				}
			}
		}
	}
	
	
	
	@Override
	public boolean hasNext() { return this.iteratorQueue.size() != 0; }
	
	@Override
	public Integer next()
	{
		if (!this.hasNext())
		{
			throw new NoSuchElementException();
		}
		
		Integer index = this.iteratorQueue.poll();
		return index;
	}
	
	
	/** Unimplemented */
	@Override
	public void remove() { throw new UnsupportedOperationException("remove"); }
	
	@Override
	public void forEachRemaining(Consumer<? super Integer> action) { Iterator.super.forEachRemaining(action); }
	
}
