package net.distanthorizons.core.util.objects.dataStreams;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import net.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import net.distanthorizons.core.logging.DhLoggerBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.distanthorizons.core.logging.DhLogger;
import org.tukaani.xz.ResettableArrayCache;
import org.tukaani.xz.XZInputStream;

import java.io.*;

/**
 * Combines multiple different streams together for ease of use
 * and to prevent accidentally wrapping a stream twice or passing in
 * the wrong stream. <br><br>
 *
 * <strong>Note:</strong>
 * This stream cannot be closed,
 * the passed in stream must be closed instead.
 * This is done to prevent closing file channels prematurely while accessing them.
 */
public class DhDataInputStream extends DataInputStream
{
	private static final ThreadLocal<ResettableArrayCache> LZMA_RESETTABLE_ARRAY_CACHE_GETTER = ThreadLocal.withInitial(() -> new ResettableArrayCache(new LzmaArrayCache()));
	
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public static DhDataInputStream create(ByteArrayList byteArrayList, EDhApiDataCompressionMode compressionMode) throws IOException
	{ return create(byteArrayList.toByteArray(), compressionMode); }
	public static DhDataInputStream create(byte[] byteArray, EDhApiDataCompressionMode compressionMode) throws IOException
	{
		// Z_Std handling compression outside the stream provides a significant performance boost
		ByteArrayInputStream byteArrayInputStream;
		if (compressionMode == EDhApiDataCompressionMode.Z_STD_BLOCK)
		{
			byteArrayInputStream = new ByteArrayInputStream(Zstd.decompress(byteArray));
		}
		else
		{
			byteArrayInputStream = new ByteArrayInputStream(byteArray);
		}
		
		return new DhDataInputStream(byteArrayInputStream, compressionMode);
	}
	private DhDataInputStream(ByteArrayInputStream stream, EDhApiDataCompressionMode compressionMode) throws IOException
	{ 
		super(warpStream(stream, compressionMode)); 
	}
	@SuppressWarnings("deprecation")
	private static InputStream warpStream(ByteArrayInputStream stream, EDhApiDataCompressionMode compressionMode) throws IOException
	{
		try
		{
			switch (compressionMode)
			{
				case UNCOMPRESSED:
					return stream;
				case LZ4:
					return new LZ4FrameInputStream(stream);
				case Z_STD_BLOCK:
					// ZStd compression should be handled before this point
					// just return the stream
					return stream;
				case LZMA2:
					// using an array cache significantly reduces GC pressure
					ResettableArrayCache arrayCache = LZMA_RESETTABLE_ARRAY_CACHE_GETTER.get();
					arrayCache.reset();
					// Note: all LZMA/XZ compressors can be decompressed using this same InputStream
					return new XZInputStream(stream, arrayCache);
				
				case Z_STD_STREAM: // deprecated, only used for legacy support
					return new ZstdInputStream(stream, RecyclingBufferPool.INSTANCE);
					
				default:
					throw new IllegalArgumentException("No compressor defined for [" + compressionMode + "]");
			}
		}
		catch (Error e)
		{
			// Should only happen if there's a library issue
			LOGGER.error("Unexpected error when wrapping DhDataInputStream, error: ["+e.getMessage()+"].", e);
			throw e;
		}
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override 
	public int read() throws IOException
	{
		try
		{
			return super.read();
		}
		catch (EOFException ignore) 
		{
			// there's a bug with XZInputStream
			// where it throws an EOFException instead
			// of returning -1 as defined by DataInputStream.read()
			return -1;
		}
		catch (IOException e)
		{
			// LZ4 has the same bug as XZ (listed above)
			// just with a slightly different exception and error message
			if (e.getMessage().equals("Stream ended prematurely"))
			{
				return -1;
			}
			else
			{
				throw e;
			}
		}
	}
	
	
	
}
