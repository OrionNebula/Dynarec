package net.lotrek.dynarec.devices;

import java.util.Arrays;

public class DirectoryDevice extends MemorySpaceDevice
{
	/*
	 * DirectoryDevice
	 * {
	 *		byte status
	 *		byte devCount
	 *		byte depStart
	 *		byte depLength
	 *		int depAddr
	 * }
	 * 
	 * DeviceEntry
	 * {
	 * 		int deviceHash
	 * 		int deviceAddress
	 * 		byte occupationLength
	 * }
	 */
	private static final Structure memStruct = new Structure(Byte.class, Byte.class, Byte.class, Byte.class, Integer.class), entryStruct = new Structure(Integer.class, Integer.class, Byte.class);
	private Register[] memInst;
	
	public int getOccupationLength()
	{
		return memStruct.getLength();
	}

	public void executeDeviceCycle()
	{
//		System.out.printf("%s: %d\n", this.getProcessor().getMemoryDevice(1).getClass().getName(), this.getProcessor().getMemoryDevice(1).getOccupationAddr());
		
		if((memInst[0].getValue(Byte.class) & 1) == 1)
		{
			int depStart = (int) memInst[2].getValue(Byte.class), depLength = (int) memInst[3].getValue(Byte.class), depAddr = memInst[4].getValue(Integer.class);
			
			System.out.printf("DeviceQuery: %d, %d, %d \n", depStart, depLength, depAddr);
			
			for(int i = 0; i < depLength; i++)
			{
				Register[] entryInst = entryStruct.getInstance(depAddr + i*entryStruct.getLength(), this.getProcessor().getMemory());
				entryInst[0].setValue(Integer.class, this.getProcessor().getMemoryDevice(depStart + i).hashCode());
				entryInst[1].setValue(Integer.class, this.getProcessor().getMemoryDevice(depStart + i).getOccupationAddr());
				entryInst[2].setValue(Byte.class, (byte)this.getProcessor().getMemoryDevice(depStart + i).getOccupationLength());
				System.out.printf("%s: %d\n", this.getProcessor().getMemoryDevice(depStart + i).getClass().getName(), this.getProcessor().getMemoryDevice(depStart + i).getOccupationAddr());
			}
			
			memInst[0].setValue(Byte.class, (byte)(memInst[0].getValue(Byte.class) & ~1));
		}
	}

	public void initializeDevice()
	{
		memInst = memStruct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
		memInst[1].setValue(Byte.class, (byte)this.getProcessor().getMemoryDeviceCount());
	}
}
