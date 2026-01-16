package com.seibel.distanthorizons.core.sql.dto;

/**
 * DTO = DataTable Object <br>
 * Any object that's stored in the database should extend this object.
 */
public interface IBaseDTO<TKey> extends AutoCloseable
{
	TKey getKey();
	/** Can be used for keys that don't have a clean human readable toString() method. */
	default String getKeyDisplayString() { return this.getKey().toString(); }
	
	// closing a DTO shouldn't have the possibility of throwing anything
	@Override 
	void close();
	
}
