package net.lotrek.dynarec.devices;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public class MouseDevice extends MemorySpaceDevice
{
	private static final Structure struct = new Structure(Byte.class, Integer.class, Integer.class);
	private Register[] inst;
	
	public int getOccupationLength()
	{
		return struct.getLength();
	}

	public void executeDeviceCycle()
	{
		if(((byte)inst[0].getValue() & 1) == 1)
		{
			inst[1].setValue(Integer.class, Mouse.getX());
			inst[2].setValue(Integer.class, Display.getHeight() - Mouse.getY() - 1);
			inst[0].setValue(Byte.class, (byte)0);
//			System.out.printf("Mouse X: %d; Mouse Y: %d;\n", Mouse.getX(), Display.getHeight() - Mouse.getY() - 1);
		}
	}

	public void initializeDevice()
	{
		inst = struct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
	}

}
