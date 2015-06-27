package net.lotrek.dynarec.devices;

import java.util.ArrayList;

public class InterruptController extends MemorySpaceDevice
{
	/*
	 * byte status - (0000 -> error code)(0000 -> has record to process)
	 * byte command - (0 = add, 1 = remove)(0000000 -> interrupt id)
	 * byte regionLength - length of the region to watch in bytes (1-8)
	 */
	private Structure controlStructure = new Structure(Byte.class, Byte.class, Integer.class, Byte.class);
	private Register[] instanceRegisters;
	private ArrayList<int[]> monitorList = new ArrayList<>();
	private ArrayList<Long> cachedValues = new ArrayList<>();
	
	public int getOccupationLength()
	{
		return controlStructure.getLength();
	}

	public void executeDeviceCycle()
	{
		if(((byte)instanceRegisters[0].getValue() & 0xf) == 1)
		{
			if(((byte)instanceRegisters[1].getValue() >> 7 & 1) == 0) //add
			{
//				System.out.println("Added interrupt " + ((byte)instanceRegisters[1].getValue() & 0x7F) + " to address " + (int)instanceRegisters[2].getValue());
				
				if(monitorList.size() > ((byte)instanceRegisters[1].getValue() & 0x7F))
					monitorList.set((byte)instanceRegisters[1].getValue() & 0x7F, new int[]{(int)instanceRegisters[2].getValue(), (byte)instanceRegisters[3].getValue()});
				else
					monitorList.add((byte)instanceRegisters[1].getValue() & 0x7F, new int[]{(int)instanceRegisters[2].getValue(), (byte)instanceRegisters[3].getValue()});
				
				if(cachedValues.size() > ((byte)instanceRegisters[1].getValue() & 0x7F))
					cachedValues.set((byte)instanceRegisters[1].getValue() & 0x7F, (long)(byte)(Byte)Register.getTypeForBytes((byte)instanceRegisters[3].getValue(), this.getProcessor().getMemory(), (int)instanceRegisters[2].getValue()));
				else
					cachedValues.add((byte)instanceRegisters[1].getValue() & 0x7F, (long)(byte)(Byte)Register.getTypeForBytes((byte)instanceRegisters[3].getValue(), this.getProcessor().getMemory(), (int)instanceRegisters[2].getValue()));
			}
			else //remove
			{
//				System.out.println("Removed interrupt " + ((byte)instanceRegisters[1].getValue() & 0x7F));
				monitorList.remove((byte)instanceRegisters[1].getValue() & 0x7F);
			}
			
			instanceRegisters[0].setValue(Byte.class, (byte)0);
		}
		
		int i = 0;
		for (int[] area : monitorList)
		{
			if((long)(byte)(Byte)Register.getTypeForBytes(area[1], this.getProcessor().getMemory(), area[0]) != cachedValues.get(i))
			{
				cachedValues.set(i, (long)(byte)Register.getTypeForBytes(area[1], this.getProcessor().getMemory(), area[0]));
				this.getProcessor().interrupt(0, i, area[0]);
			}
			
			i++;
		}
	}

	public void initializeDevice()
	{
		instanceRegisters = controlStructure.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
	}

}
