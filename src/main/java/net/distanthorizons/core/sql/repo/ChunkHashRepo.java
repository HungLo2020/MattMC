package net.distanthorizons.core.sql.repo;

import net.distanthorizons.core.logging.DhLoggerBuilder;
import net.distanthorizons.core.pos.DhChunkPos;
import net.distanthorizons.core.sql.dto.ChunkHashDTO;
import net.distanthorizons.core.logging.DhLogger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChunkHashRepo extends AbstractDhRepo<DhChunkPos, ChunkHashDTO>
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ChunkHashRepo(String databaseType, File databaseFile) throws SQLException, IOException
	{
		super(databaseType, databaseFile, ChunkHashDTO.class);
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override 
	public String getTableName() { return "ChunkHash"; }
	
	@Override
	protected String CreateParameterizedWhereString() { return "ChunkPosX = ? AND ChunkPosZ = ?"; }
	
	@Override
	protected int setPreparedStatementWhereClause(PreparedStatement statement, int index, DhChunkPos pos) throws SQLException
	{
		statement.setInt(index++, pos.getX());
		statement.setInt(index++, pos.getZ());
		return index;
	}
	
	
	
	//=======================//
	// repo required methods //
	//=======================//
	
	@Override
	@Nullable
	public ChunkHashDTO convertResultSetToDto(ResultSet resultSet) throws ClassCastException, SQLException
	{
		int posX = resultSet.getInt("ChunkPosX");
		int posZ = resultSet.getInt("ChunkPosZ");
		
		int chunkHash = resultSet.getInt("ChunkHash");
		
		
		ChunkHashDTO dto = new ChunkHashDTO(new DhChunkPos(posX, posZ), chunkHash);
		return dto;
	}
	
	private final String insertSqlTemplate =
		"INSERT INTO "+this.getTableName() + " (\n" +
		"   ChunkPosX, ChunkPosZ, \n" +
		"   ChunkHash, \n" +
		"   LastModifiedUnixDateTime, CreatedUnixDateTime) \n" +
		"VALUES( \n" +
		"    ?, ?, \n" +
		"    ?, \n" +
		"    ?, ? \n" +
		");";
	@Override
	public PreparedStatement createInsertStatement(ChunkHashDTO dto) throws SQLException
	{
		PreparedStatement statement = this.createPreparedStatement(this.insertSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		
		
		int i = 1;
		statement.setObject(i++, dto.pos.getX());
		statement.setObject(i++, dto.pos.getZ());
		
		statement.setObject(i++, dto.chunkHash);
		
		statement.setObject(i++, System.currentTimeMillis()); // last modified unix time
		statement.setObject(i++, System.currentTimeMillis()); // created unix time
		
		return statement;
	}
	
	private final String updateSqlTemplate =
		"UPDATE "+this.getTableName()+" \n" +
		"SET \n" +
		"    ChunkHash = ? \n" +
		"   ,LastModifiedUnixDateTime = ? \n" +
		"WHERE ChunkPosX = ? AND ChunkPosZ = ?";
	@Override
	public PreparedStatement createUpdateStatement(ChunkHashDTO dto) throws SQLException
	{
		PreparedStatement statement = this.createPreparedStatement(updateSqlTemplate);
		if (statement == null)
		{
			return null;
		}
		
		
		int i = 1;
		statement.setObject(i++, dto.chunkHash);
		statement.setObject(i++, System.currentTimeMillis()); // last modified unix time
		
		statement.setObject(i++, dto.pos.getX());
		statement.setObject(i++, dto.pos.getZ());
		
		return statement;
	}
	
	
	
}
