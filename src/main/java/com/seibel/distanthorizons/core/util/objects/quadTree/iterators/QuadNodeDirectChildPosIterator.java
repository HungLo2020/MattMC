package com.seibel.distanthorizons.core.util.objects.quadTree.iterators;

import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.util.objects.quadTree.QuadNode;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.NoSuchElementException;
import java.util.function.LongConsumer;

public class QuadNodeDirectChildPosIterator<T> implements LongIterator
{
	private final QuadNodeChildIndexIterator<T> childIndexIterator;
	private final QuadNode<T> parentNode;
	
	
	public QuadNodeDirectChildPosIterator(QuadNode<T> parentNode)
	{
		this.parentNode = parentNode;
		this.childIndexIterator = new QuadNodeChildIndexIterator<>(this.parentNode, true);
	}
	
	
	
	@Override
	public boolean hasNext() { return this.childIndexIterator.hasNext(); }
	
	@Override
	public long nextLong()
	{
		if (!this.hasNext())
		{
			throw new NoSuchElementException();
		}
		
		
		int childIndex = this.childIndexIterator.next();
		long sectionPos = DhSectionPos.getChildByIndex(this.parentNode.sectionPos, childIndex);
		return sectionPos;
	}
	
	
	/** Unimplemented */
	@Override
	public void remove() { throw new UnsupportedOperationException("remove"); }
	
	@Override
	public void forEachRemaining(LongConsumer action) { LongIterator.super.forEachRemaining(action); }
	
}