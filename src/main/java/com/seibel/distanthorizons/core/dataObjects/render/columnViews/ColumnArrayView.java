package com.seibel.distanthorizons.core.dataObjects.render.columnViews;


import com.seibel.distanthorizons.core.dataObjects.render.ColumnRenderSource;
import com.seibel.distanthorizons.core.util.RenderDataPointUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.util.Arrays;
import net.vulkanic.world.NativeDhRenderSource;
import java.util.ConcurrentModificationException;

public final class ColumnArrayView implements IColumnDataView
{
	public final LongList data;
	
	/** 
	 * How many data points are currently being represented by this view. <br>
	 * Will be equal to or less than {@link ColumnArrayView#verticalSize}.
	 */
	public final int size;
	/** 
	 * Vertical size in data points. <Br>
	 * Can be 0 if this column was created for an empty data source.
	 */
	public final int verticalSize;
	
	/**
	 * Where the relative starting index is in the {@link ColumnArrayView#data} array
	 * if this view is representing part of a {@link ColumnRenderSource}.
	 */
	public final int offset;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	/** @throws IllegalArgumentException if the offset is greater than the data's size */
	public ColumnArrayView(LongList data, int size, int offset, int verticalSize) throws IllegalArgumentException
	{
		this.data = data;
		this.size = size;
		this.offset = offset;
		this.verticalSize = verticalSize;
		
		if (this.data.size() < this.offset)
		{
			throw new IllegalArgumentException("data size ["+this.data.size()+"] is shorter than offset ["+this.offset+"].");
		}
	}
	
	
	
	//=====================//
	// getters and setters //
	//=====================//
	
	@Override
	public long get(int index) 
	{
		try
		{
			return this.data.getLong(index + this.offset);
		}
		catch (IndexOutOfBoundsException e)
		{
			// we can fairly confidently say this is a concurrent exception over an actual
			// index out of bounds, since we're generally iterating over the whole
			// array any time we use this getter.
			throw new ConcurrentModificationException("Potential concurrent modification detected. Make sure the parent ColumnRenderSource isn't being closed before the ColumnArrayView processing is complete.", e);
		}
	}
	public void set(int index, long value) { data.set(index + offset, value); }
	
	@Override
	public int size() { return size; }
	@Override
	public int verticalSize() { return verticalSize; }
	
	@Override
	public int dataCount() { return (this.verticalSize != 0) ? (this.size / this.verticalSize) : 0; } // TODO what does the divide by mean?
	
	@Override
	public ColumnArrayView subView(int dataIndexStart, int dataCount)
	{ return new ColumnArrayView(data, dataCount * verticalSize, offset + dataIndexStart * verticalSize, verticalSize); }
	
	public void fill(long value) {
		if (data instanceof NativeDhRenderSource) NativeDhRenderSource.span(data, offset, size, 0, value);
		else if (data instanceof LongArrayList array) Arrays.fill(array.elements(), offset, offset + size, value);
		else for (int i = 0; i < size; i++) data.set(offset + i, value);
	}
	
	@Override
	public void copyTo(long[] target, int offset, int size) { data.getElements(this.offset, target, offset, size); }
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("S:").append(this.size);
		sb.append(" V:").append(this.verticalSize);
		sb.append(" O:").append(this.offset);
		
		sb.append(" [");
		for (int i = 0; i < this.size; i++)
		{
			sb.append(RenderDataPointUtil.toString(this.data.getLong(this.offset + i)));
			if (i < this.size - 1)
			{
				sb.append(",\n");
			}
		}
		sb.append("]");
		
		return sb.toString();
	}
	
	
	public int getDataHash() { return data instanceof NativeDhRenderSource
		? NativeDhRenderSource.span(data, offset, size, 1, 0L) : arrayHash(data, offset, size); }
	private static int arrayHash(LongList a, int offset, int length)
	{
		if (a == null)
		{
			return 0;
		}
		
		int result = 1;
		int end = offset + length;
		for (int i = offset; i < end; i++)
		{
			long element = a.getLong(i);
			int elementHash = (int) (element ^ (element >>> 32));
			result = 31 * result + elementHash;
		}
		return result;
	}
	
	
	
}