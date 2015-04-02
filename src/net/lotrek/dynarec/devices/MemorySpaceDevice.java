package net.lotrek.dynarec.devices;

import net.lotrek.dynarec.execute.Processor;

public abstract class MemorySpaceDevice
{
	private Processor proc;
	private int occupationStart = -1;
	
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
	
	public abstract int getOccupationLength();
	public abstract void executeDeviceCycle();
	public abstract void initializeDevice();
}
