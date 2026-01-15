package net.distanthorizons.core.dataObjects.render.columnViews;

import it.unimi.dsi.fastutil.longs.LongIterator;

public interface IColumnDataView
{
	long get(int index);
	
	// FIXME probably horizontal size in blocks?
	int size();
	
	default LongIterator iterator()
	{
		return new LongIterator()
		{
			private int index = 0;
			private final int size = IColumnDataView.this.size();
			
			@Override
			public boolean hasNext() { return this.index < this.size; }
			
			@Override
			public long nextLong() { return IColumnDataView.this.get(this.index++); }
			
		};
	}
	
	// FIXME measured in blocks?
	int verticalSize();
	
	// FIXME how many datapoints in this LOD?
	int dataCount();
	
	IColumnDataView subView(int dataIndexStart, int dataCount);
	
	void copyTo(long[] target, int offset, int count);
	
}
