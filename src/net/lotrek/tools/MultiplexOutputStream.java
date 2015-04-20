package net.lotrek.tools;
import java.io.*;

public class MultiplexOutputStream extends OutputStream
{
	private OutputStream[] streams;
	
	public MultiplexOutputStream(OutputStream...outputStreams)
	{
		this.streams = outputStreams;
	}
	
	@Override
	public void write(int b) throws IOException
	{
		for(OutputStream stream : streams)
			stream.write(b);
	}
	
	public void write(byte[] b) throws IOException
	{
		for(OutputStream stream : streams)
				stream.write(b);
		this.flush();
	}
	
	@Override
	public void flush() throws IOException
	{
		for(OutputStream stream : streams)
			stream.flush();
	}
	
	public static void writeInputToOutput(InputStream is, OutputStream os, boolean close)
	{
		try {
			byte[] b = new byte[1024];
			int read = 0;
			while((read = is.read(b)) != -1)
				os.write(b, 0, read);
			if(close)
				is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static byte[] readFully(InputStream is, boolean close)
	{
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		writeInputToOutput(is, os, close);
		return os.toByteArray();
	}
	
	public static void multiplexConsole(OutputStream...outputStreams)
	{
		OutputStream[] outputStreams2 = new OutputStream[outputStreams.length + 1];
		System.arraycopy(outputStreams, 0, outputStreams2, 0,
				outputStreams.length);
		outputStreams2[outputStreams.length] = System.out;
		
		System.setOut(new TimestampPrintStream(new MultiplexOutputStream(outputStreams2)));
		
		System.setErr(System.out);
	}
	
	public static void logConsole(String file)
	{
		try {
			MultiplexOutputStream.multiplexConsole(new FileOutputStream(new File(file), false));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
