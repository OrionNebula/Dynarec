package net.lotrek.tools;


import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TimestampPrintStream extends PrintStream
{
	public TimestampPrintStream(OutputStream out)
	{
		super(out);
	}

	@Override
	public PrintStream printf(String format, Object... args)
	{
		return super.printf("[" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SS").format(new Date()) + " " + Thread.currentThread().getStackTrace()[2].getClassName().split("\\.")[Thread.currentThread().getStackTrace()[2].getClassName().split("\\.").length - 1] +  Thread.currentThread().getStackTrace()[2].toString().substring(Thread.currentThread().getStackTrace()[2].getClassName().length()) + "] " + format, args);
	}

	public void println(Object x)
	{
		super.println("[" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SS").format(new Date()) + " " + Thread.currentThread().getStackTrace()[2].getClassName().split("\\.")[Thread.currentThread().getStackTrace()[2].getClassName().split("\\.").length - 1] +  Thread.currentThread().getStackTrace()[2].toString().substring(Thread.currentThread().getStackTrace()[2].getClassName().length()) + "] " + x);
	}
	
	public void println(String x)
	{
		super.println("[" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SS").format(new Date()) + " " + Thread.currentThread().getStackTrace()[2].getClassName().split("\\.")[Thread.currentThread().getStackTrace()[2].getClassName().split("\\.").length - 1] +  Thread.currentThread().getStackTrace()[2].toString().substring(Thread.currentThread().getStackTrace()[2].getClassName().length()) + "] " + x);
	}
	
	public void println(boolean x)
	{
		super.println("[" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SS").format(new Date()) + " " + Thread.currentThread().getStackTrace()[2].getClassName().split("\\.")[Thread.currentThread().getStackTrace()[2].getClassName().split("\\.").length - 1] +  Thread.currentThread().getStackTrace()[2].toString().substring(Thread.currentThread().getStackTrace()[2].getClassName().length()) + "] " + x);
	}
	
	public void println(int x)
	{
		super.println("[" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SS").format(new Date()) + " " + Thread.currentThread().getStackTrace()[2].getClassName().split("\\.")[Thread.currentThread().getStackTrace()[2].getClassName().split("\\.").length - 1] +  Thread.currentThread().getStackTrace()[2].toString().substring(Thread.currentThread().getStackTrace()[2].getClassName().length()) + "] " + x);
	}
	
	public void println(float x)
	{
		super.println("[" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SS").format(new Date()) + " " + Thread.currentThread().getStackTrace()[2].getClassName().split("\\.")[Thread.currentThread().getStackTrace()[2].getClassName().split("\\.").length - 1] +  Thread.currentThread().getStackTrace()[2].toString().substring(Thread.currentThread().getStackTrace()[2].getClassName().length()) + "] " + x);
	}
	
	public void println(double x)
	{
		super.println("[" + new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SS").format(new Date()) + " " + Thread.currentThread().getStackTrace()[2].getClassName().split("\\.")[Thread.currentThread().getStackTrace()[2].getClassName().split("\\.").length - 1] +  Thread.currentThread().getStackTrace()[2].toString().substring(Thread.currentThread().getStackTrace()[2].getClassName().length()) + "] " + x);
	}
}
