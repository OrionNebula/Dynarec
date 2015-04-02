package net.lotrek.dynarec.devices;

public class Structure
{
	private Class<?>[] registerDefs;
	
	public Structure(Class<?>...registers)
	{
		registerDefs = registers;
	}
	
	public Register[] getInstance(int addr, byte[] memory)
	{
		Register[] toReturn = new Register[registerDefs.length];
		
		int offset = 0;
		for (int i = 0; i < registerDefs.length; i++) {
			@SuppressWarnings("unchecked")
			Register reg = new Register((Class<? extends Number>)registerDefs[i], addr + offset, memory);
			offset += reg.getTypeLength();
			toReturn[i] = reg;
		}
		
		return toReturn;
	}
	
	@SuppressWarnings("unchecked")
	public int getLength()
	{
		int tot = 0;
		for (int i = 0; i < registerDefs.length; i++)
			tot += Register.getTypeLength((Class<? extends Number>) registerDefs[i]);
		
		return tot;
	}
}
