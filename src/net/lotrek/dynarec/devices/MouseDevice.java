package net.lotrek.dynarec.devices;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public class MouseDevice extends MemorySpaceDevice
{
	/*
	 * int xPos
	 * int yPos
	 * byte buttons
	 */
	private static final Structure struct = new Structure(Integer.class, Integer.class, Byte.class);
	private Register[] inst;
	
	public int getOccupationLength()
	{
		return struct.getLength();
	}

	public void executeDeviceCycle()
	{
		if(Mouse.isCreated())
		{
			inst[0].setValue(Integer.class, Mouse.getX());
			inst[1].setValue(Integer.class, Display.getHeight() - Mouse.getY() - 1);
			inst[2].setValue(Byte.class, (byte)((Mouse.isButtonDown(0) ? 1 : 0) | (Mouse.isButtonDown(1) ? 0b10 : 0) | (Mouse.isButtonDown(2) ? 0b100 : 0)));
		}
	}

	public void initializeDevice()
	{
		inst = struct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
	}

	public void disposeDevice(){}
}
