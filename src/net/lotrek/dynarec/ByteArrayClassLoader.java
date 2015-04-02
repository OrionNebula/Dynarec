package net.lotrek.dynarec;

public class ByteArrayClassLoader extends ClassLoader
{
	public Class<?> loadClass(String name, byte[] data, int off, int len) throws ClassNotFoundException
	{
		return this.defineClass(name, data, off, len);
	}
}
