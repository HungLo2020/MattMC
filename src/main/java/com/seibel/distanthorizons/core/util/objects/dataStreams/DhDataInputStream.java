package com.seibel.distanthorizons.core.util.objects.dataStreams;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;
import com.seibel.distanthorizons.api.enums.config.EDhApiDataCompressionMode;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import net.jpountz.lz4.LZ4FrameInputStream;
import com.seibel.distanthorizons.core.logging.DhLogger;
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
    private final boolean codecByteArrayInput;
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
			byteArrayInputStream = new CodecBytes(Zstd.decompress(byteArray));
		}
		else
		{
			byteArrayInputStream = new CodecBytes(byteArray);
		}
		
		return new DhDataInputStream(byteArrayInputStream, compressionMode);
	}
	private DhDataInputStream(ByteArrayInputStream stream, EDhApiDataCompressionMode compressionMode) throws IOException
	{ 
		super(warpStream(stream, compressionMode));
        codecByteArrayInput = compressionMode == EDhApiDataCompressionMode.UNCOMPRESSED
            || compressionMode == EDhApiDataCompressionMode.Z_STD_BLOCK;
        codecBytes = codecByteArrayInput && stream instanceof CodecBytes bytes ? bytes : null;
	}
    /** The decompressed bytes of a byte-array stream, readable in place by native decoders. */
    private static final class CodecBytes extends ByteArrayInputStream {
        CodecBytes(byte[] bytes) { super(bytes); }
        byte[] array() { return this.buf; }
        int position() { return this.pos; }
        int limit() { return this.count; }
        void seek(int position) { java.util.Objects.checkIndex(position, this.count + 1); this.pos = position; }
    }
    private final CodecBytes codecBytes;
    /** For {@link #codecByteArrayInput()} streams: the whole decompressed array, read in place.
     * {@link DataInputStream} keeps no read-ahead, so the position below is exact. */
    public byte[] codecArray() { return this.codec().array(); }
    public int codecPosition() { return this.codec().position(); }
    public int codecLimit() { return this.codec().limit(); }
    /** Moves the read position, as a native decoder consumes or a rereader rewinds. */
    public void codecSeek(int position) { this.codec().seek(position); }
    private CodecBytes codec() {
        if (this.codecBytes == null) throw new IllegalStateException("Not a byte-array DH stream");
        return this.codecBytes;
    }
    /** These exact private wrappers cannot fail midway through a byte-array
     * read. Streaming decompressors retain readByte's original failure prefix. */
    public boolean codecByteArrayInput() { return codecByteArrayInput; }
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
