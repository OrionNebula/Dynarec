package net.lotrek.dynarec.execute;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Assembler
{
	public Class<? extends Processor> getProcessorType();
	public void assemble(InputStream is, OutputStream os, AssemblyType type) throws IOException;
	
	public static enum AssemblyType
	{
		FLAT_FILE,
		ACCEDEFF
	}
}
