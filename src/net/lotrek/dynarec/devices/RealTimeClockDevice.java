package net.lotrek.dynarec.devices;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import net.lotrek.dynarec.execute.Processor;

public class RealTimeClockDevice extends MemorySpaceDevice
{
	/*
	 * byte interruptId
	 * byte command
	 * int period (millis)
	 */
	private static final Structure rtcStruct = new Structure(Byte.class, Byte.class, Integer.class); 
	private Register[] instance;
	private ArrayList<Timer> events = new ArrayList<>();
	
	public int getOccupationLength()
	{
		return rtcStruct.getLength();
	}

	public void executeDeviceCycle()
	{
		if(instance[0].getValue(Byte.class) != 0)
		{
			switch(instance[1].getValue(Byte.class))
			{
			case 0:
//				System.out.println("Event " + instance[0].getValue(Byte.class) + " added; " + instance[2].getValue(Integer.class) + " ms");
				Timer t = new Timer();
				events.add(t);
				t.schedule(new InterruptTask((int) instance[0].getValue(Byte.class), this.getProcessor()), 0, (long) instance[2].getValue(Integer.class));
				break;
			case 1:
				events.remove((int) instance[0].getValue(Byte.class) - 1).cancel();
				break;
			}
			
			instance[0].setValue(Byte.class, (byte)0);
		}
	}

	public void initializeDevice()
	{
		instance = rtcStruct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
	}
	
	private static class InterruptTask extends TimerTask
	{
		private int id;
		private Processor proc;
		public InterruptTask(int id, Processor proc)
		{
			this.id = id;
			this.proc = proc;
		}
		
		public void run()
		{
			proc.interrupt(1, id);
		}
		
	}
}
