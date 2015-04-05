package net.lotrek.dynarec.execute;

import net.lotrek.dynarec.devices.MemorySpaceDevice;

public abstract class Processor
{
	protected MemorySpaceDevice[] devices;
	protected byte[] memory;
	private int lastAllocationOffset;
	private Runnable peripheralRunnable = () -> {
		for (MemorySpaceDevice memorySpaceDevice : devices)
			memorySpaceDevice.initializeDevice();
		
		while(!Thread.currentThread().isInterrupted())
			for (MemorySpaceDevice memorySpaceDevice : devices)
				memorySpaceDevice.executeDeviceCycle();
	};
	private Thread peripheralThread = new Thread(peripheralRunnable);
	
	public Processor(int memorySize, byte[] biosImage, MemorySpaceDevice...memorySpaceDevices)
	{
		memory = new byte[memorySize];
		devices = memorySpaceDevices;
		
		for (MemorySpaceDevice memorySpaceDevice : memorySpaceDevices)
		{
			memorySpaceDevice.setOccupationAddr(memorySize - (lastAllocationOffset += memorySpaceDevice.getOccupationLength()));
			memorySpaceDevice.setProcessor(this);
		}
		
		System.arraycopy(biosImage, 0, memory, 0, biosImage.length);
	}
	
	public byte[] getMemory()
	{
		return memory;
	}
	
	public int getMemorySize()
	{
		return memory.length;
	}
	
	public int getAvailableMemory()
	{
		return getMemorySize() - lastAllocationOffset;
	}
	
	public final void startProcessor()
	{
		if(devices.length > 0)
			peripheralThread.start();
		
		executeImpl();
	}

	public final void terminateProcessor()
	{
		peripheralThread.interrupt();
		try {
			peripheralThread.join();
			terminateImpl();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	protected abstract void terminateImpl();
	protected abstract void executeImpl();
	public abstract void interrupt(int index, long...parameters);
}
