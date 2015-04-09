package net.lotrek.dynarec.devices;

import java.util.concurrent.LinkedBlockingQueue;

import org.lwjgl.input.Keyboard;

public class KeyboardDevice extends MemorySpaceDevice
{
	private Structure struct = new Structure(Byte.class, Byte.class, Integer.class, Integer.class, Integer.class);
	private Register[] instanceRegisters;
	private LinkedBlockingQueue<Byte[]> keyboardData = new LinkedBlockingQueue<>();
	
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
					keyboardData.add(new Byte[]{(byte) Keyboard.getEventKey(), (byte) Keyboard.getEventCharacter()});
		
		if(oldSiz != keyboardData.size())
			instanceRegisters[0].setValue(Byte.class, (byte)(instanceRegisters[0].getValue(Byte.class) == 0 ? 1 : 0));
		
		if(instanceRegisters[1].getValue(Byte.class) != 0)
		{
			int length = instanceRegisters[3].getValue(Integer.class), addr = instanceRegisters[4].getValue(Integer.class);
			
			byte[] b = new byte[length * 2];
			for (int i = 0; i < b.length && !keyboardData.isEmpty(); i += 2) {
				Byte[] data = keyboardData.poll();
				b[i] = data[0]; 
				b[i + 1] = data[1];
			}
			
			System.arraycopy(b, 0, getProcessor().getMemory(), addr, length * 2);
			
			instanceRegisters[1].setValue(Byte.class, (byte)0);
		}
		
		instanceRegisters[2].setValue(Integer.class, keyboardData.size());
	}

	public void initializeDevice()
	{
		instanceRegisters = struct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
	}

}
