package net.lotrek.dynarec.devices;

public class Register
{
	private Class<? extends Number> type;
	private int typeLength, addr;
	private byte[] memoryRef;
	
	public Register(Class<? extends Number> type, int addr, byte[] mem)
	{
		memoryRef = mem;
		this.addr = addr;
		
		try {
			this.type = type;
			typeLength = type.getField("SIZE").getInt(null) / 8;
		} catch (IllegalArgumentException | IllegalAccessException
				| NoSuchFieldException | SecurityException e) {
		}
	}
	
	public static int getTypeLength(Class<? extends Number> type)
	{
		try {
			return type.getField("SIZE").getInt(null) / 8;
		} catch (IllegalArgumentException | IllegalAccessException
				| NoSuchFieldException | SecurityException e) {
			e.printStackTrace();
		}
		
		return 0;
	}
	
	public static <T extends Number> byte[] getBytesForType(Class<T> type, T value)
	{
		byte[] num = new byte[getTypeLength(type)];
		
		switch(type.getSimpleName())
		{
		case "Byte":
			num[0] = (byte) value;
			break;
		case "Short":
			for (int i = 0; i < 2; i++)
			    num[1 - i] = (byte)((short)value >>> (i * 8));
			break;
		case "Integer":
			for (int i = 0; i < 4; i++)
			    num[3 - i] = (byte)((int)value >>> (i * 8));
			break;
		case "Float":
			for (int i = 0; i < 4; i++)
			    num[3 - i] = (byte)(Float.floatToRawIntBits((float)value) >>> (i * 8));
			break;
		case "Long":
			for (int i = 0; i < 8; i++)
			    num[7 - i] = (byte)((long)value >>> (i * 8));
			break;
		case "Double":
			for (int i = 0; i < 8; i++)
			    num[7 - i] = (byte)(Double.doubleToLongBits((double)value) >>> (i * 8));
			break;
		}
		
		return num;
	}
	
	public static <T extends Number> Object getTypeForBytes(Class<T> type, byte[] b, int off)
	{
		long value = 0;
		for (int i = off; i < off + getTypeLength(type); i++)
			value = (value << 8) + (b[i] & 0xff);
		
		switch(type.getSimpleName())
		{
		case "Byte":
			return b[off];
		case "Short":
			return (short)value;
		case "Integer":
			return (int)value;
		case "Float":
			return (float)Float.intBitsToFloat((int)value);
		case "Long":
			return value;
		case "Double":
			return (double)Double.longBitsToDouble(value);
		}
		
		return null;
	}
	
	public static Object getTypeForBytes(int len, byte[] b, int off)
	{
		long value = 0;
		for (int i = off; i < off + len; i++)
			value = (value << 8) + (b[i] & 0xff);
		
		switch(len)
		{
		case 1:
			return b[off];
		case 2:
			return (short)value;
		case 4:
			return (int)value;
		case 8:
			return value;
		}
		
		return null;
	}
	
	public int getAddress()
	{
		return addr;
	}
	
	public Object getValue()
	{
		long value = 0;
		for (int i = addr; i < addr + getTypeLength(); i++)
			value = (value << 8) + (memoryRef[i] & 0xff);
		
		switch(type.getSimpleName())
		{
		case "Byte":
			return memoryRef[addr];
		case "Short":
			return (short)value;
		case "Integer":
			return (int)value;
		case "Float":
			return (float)Float.intBitsToFloat((int)value);
		case "Long":
			return value;
		case "Double":
			return (double)Double.longBitsToDouble(value);
		}
		
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getValue(Class<T> type)
	{
		long value = 0;
		for (int i = addr; i < addr + getTypeLength(); i++)
			value = (value << 8) + (memoryRef[i] & 0xff);
		
		switch(type.getSimpleName())
		{
		case "Byte":
			return (T)(Byte)memoryRef[addr];
		case "Short":
			return (T)(Short)(short)value;
		case "Integer":
			return (T)(Integer)(int)value;
		case "Float":
			return (T)(Float)(float)Float.intBitsToFloat((int)value);
		case "Long":
			return (T)(Long)value;
		case "Double":
			return (T)(Double)(double)Double.longBitsToDouble(value);
		}
		
		return null;
	}
	
	public <T extends Number> void setValue(Class<T> type, T value)
	{
		if(!type.equals(this.type))
			throw new IllegalArgumentException("Attempted type and predicted type don't match: " + type + " vs intended " + this.type);

		byte[] num = getBytesForType(type, value);
		
		System.arraycopy(num, 0, memoryRef, addr, num.length);
	}
	
	public int getTypeLength()
	{
		return typeLength;
	}
	
	public String toString()
	{
		return String.format("Register{type : %s, addr : %d, size : %d, value : %s}", type.getSimpleName(), addr, this.getTypeLength() * 8, getValue().toString());
	}
}
