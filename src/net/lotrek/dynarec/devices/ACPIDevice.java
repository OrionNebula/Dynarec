package net.lotrek.dynarec.devices;

import java.util.Date;

public class ACPIDevice extends MemorySpaceDevice
{
	/*
	 * byte status
	 * int cmdAddr
	 * long launchTime
	 * long currentTime
	 */
	private static final Structure memStruct = new Structure(Byte.class, Integer.class, Long.class, Long.class),
			/*
			 * byte mode
			 * int data
			 */
			cmdStruct = new Structure(Byte.class, Integer.class);
	private Register[] instance;
	private long initDate;
	private int interruptID;
	
	public int getOccupationLength()
	{
		return memStruct.getLength();
	}

	public void executeDeviceCycle()
	{
		instance[3].setValue(Long.class, new Date().getTime());
		if((instance[0].getValue(Byte.class) & 1) != 0)
		{
			Register[] cmd = cmdStruct.getInstance(instance[1].getValue(Integer.class), this.getProcessor().getMemory());
			switch(cmd[0].getValue(Byte.class))
			{
			case 0: //Initiate shutdown
				this.getProcessor().terminateProcessor(false);
				break;
			case 1: //Initiate reboot
				this.getProcessor().terminateProcessor(true);
				break;
			case 2: //Register interrupt for power notifications
				interruptID = cmd[1].getValue(Integer.class);
				break;
			}
			
			instance[0].setValue(Byte.class, (byte)(instance[0].getValue(Byte.class) & ~1));
		}
	}

	public void initializeDevice()
	{
		initDate = new Date().getTime();
		instance = memStruct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
		instance[2].setValue(Long.class, initDate);
		instance[3].setValue(Long.class, initDate);
	}
	
	public void disposeDevice(){}
	
	public void triggerACPIShutdown()
	{
		this.getProcessor().interrupt(interruptID);
	}

}
