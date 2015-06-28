package net.lotrek.dynarec.devices;

import net.lotrek.dynarec.execute.Processor;

public abstract class MemorySpaceDevice
{
	private Processor proc;
	private int occupationStart = -1;
	
	public MemorySpaceDevice()
	{
		System.out.println("Created device \"" + this.getClass().getSimpleName() + "\" with hashcode " + Integer.toHexString(this.hashCode()));
	}
	
	public void setProcessor(Processor proc)
	{
		this.proc = proc;
	}
	
	public Processor getProcessor()
	{
		return this.proc;
	}
	
	public void setOccupationAddr(int addr)
	{
		this.occupationStart = this.occupationStart == -1 ? addr : occupationStart;
	}
	
	public int getOccupationAddr()
	{
		return occupationStart;
	}
	
	public final int hashCode()
	{
		return this.getClass().getName().hashCode();
	}
	
	public abstract int getOccupationLength();
	public abstract void executeDeviceCycle();
	public abstract void initializeDevice();
	public abstract void disposeDevice();
}
