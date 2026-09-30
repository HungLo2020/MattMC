package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.DhLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil
{
	private static final DhLogger LOGGER = new DhLoggerBuilder().build();
	
	
	/** Returns the content of the given file as a string. */
	public static String readFile(File file, Charset encoding) throws IOException
	{
		byte[] encoded = Files.readAllBytes(file.toPath());
		return new String(encoded, encoding);
	}
	
}
