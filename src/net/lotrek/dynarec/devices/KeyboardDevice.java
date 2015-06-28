package net.lotrek.dynarec.devices;

import java.util.concurrent.LinkedBlockingQueue;

import org.lwjgl.input.Keyboard;

public class KeyboardDevice extends MemorySpaceDevice
{
	/*
	 * byte swap - swaps between 1/0 on keypress
	 * byte busy - indicates a command to be processed
	 * int buffLen - length available to read
	 * int readLen - length to read
	 * int addr - address to read to
	 */
	private Structure struct = new Structure(Byte.class, Byte.class, Integer.class, Integer.class, Integer.class),
			/*
			 * int key
			 * byte char
			 */
			keyStroke = new Structure(Integer.class, Byte.class);
	private Register[] instanceRegisters;
	private LinkedBlockingQueue<Object[]> keyboardData = new LinkedBlockingQueue<>();
	
	public int getOccupationLength()
	{
		return struct.getLength();
	}

	public void executeDeviceCycle()
	{
		int oldSiz = keyboardData.size();
		if(Keyboard.isCreated())
			while(Keyboard.next())
				if(Keyboard.getEventKeyState())
					keyboardData.add(new Object[]{Keyboard.getEventKey(), (byte) Keyboard.getEventCharacter()});
		
		if(oldSiz != keyboardData.size())
			instanceRegisters[0].setValue(Byte.class, (byte)(instanceRegisters[0].getValue(Byte.class) == 0 ? 1 : 0));
		
		if(instanceRegisters[1].getValue(Byte.class) != 0)
		{
			int length = instanceRegisters[3].getValue(Integer.class), addr = instanceRegisters[4].getValue(Integer.class);
			
			for (int i = 0; i < length * keyStroke.getLength() && !keyboardData.isEmpty(); i += keyStroke.getLength()) {
				Object[] data = keyboardData.poll();
				Register[] inst = keyStroke.getInstance(addr + i, this.getProcessor().getMemory());
				inst[0].setValue(Integer.class, (int)data[0]);
				inst[1].setValue(Byte.class, (byte)data[1]);
			}
			
			instanceRegisters[1].setValue(Byte.class, (byte)0);
		}
		
		instanceRegisters[2].setValue(Integer.class, keyboardData.size());
	}

	public void initializeDevice()
	{
		instanceRegisters = struct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
	}

	public void disposeDevice(){}
}
