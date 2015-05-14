package net.lotrek.dynarec.execute;

import javax.swing.JPanel;

import net.lotrek.dynarec.devices.DirectoryDevice;
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
		devices = new MemorySpaceDevice[1 + memorySpaceDevices.length];//memorySpaceDevices;
		System.arraycopy(memorySpaceDevices, 0, devices, 1, memorySpaceDevices.length);
		devices[0] = new DirectoryDevice();
		
		for (MemorySpaceDevice memorySpaceDevice : devices)
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
	
	public final int getMemoryDeviceCount()
	{
		return devices.length;
	}
	
	public final MemorySpaceDevice getMemoryDevice(int id)
	{
		return devices[id];
	}
	
	protected abstract void terminateImpl();
	protected abstract void executeImpl();
	public abstract void interrupt(int index, long...parameters);
	public abstract void startDebugPane();
	public abstract JPanel getPanelForDevice(MemorySpaceDevice dev);
}
