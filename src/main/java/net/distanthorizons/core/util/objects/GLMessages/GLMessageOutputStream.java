package net.distanthorizons.core.util.objects.GLMessages;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Consumer;

public final class GLMessageOutputStream extends OutputStream
{
	final Consumer<GLMessage> func;
	final GLMessageBuilder builder;
	
	
	private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	
	public GLMessageOutputStream(Consumer<GLMessage> func, GLMessageBuilder builder)
	{
		this.func = func;
		this.builder = builder;
	}
	
	@Override
	public void write(int b)
	{
		this.buffer.write(b);
		if (b == '\n')
		{
			this.flush();
		}
	}
	
	@Override
	public void flush()
	{
		String str = this.buffer.toString();
		GLMessage msg = this.builder.add(str);
		if (msg != null)
		{
			this.func.accept(msg);
		}
		this.buffer.reset();
	}
	
	@Override
	public void close() throws IOException
	{
		this.flush();
		this.buffer.close();
	}
	
}
