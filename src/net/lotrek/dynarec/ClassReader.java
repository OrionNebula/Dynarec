package net.lotrek.dynarec;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ClassReader
{
	public static void parseClass(InputStream cls) throws IOException
	{
		DataInputStream dis = new DataInputStream(cls);
		
		if(dis.readInt() != 0xCAFEBABE)
		{
			System.out.println("File is not a class file");
			return;
		}
		
		System.out.println("File contains 0xCAFEBABE header");
		System.out.printf("Major version: %d, Minor version: %d\n", dis.readUnsignedShort(), dis.readUnsignedShort());
		int poolCount = dis.readUnsignedShort();
		System.out.printf("Contains %d constpool entries\n", poolCount);
		
		Object[][] entries = new Object[poolCount][];
		
		for (int i = 1; i < poolCount; i++)
		{
			int tag = dis.readUnsignedByte();
			
			System.out.print(" ");
			switch(tag)
			{
			case 7: //CONSTANT_Class
				entries[i - 1] = new Object[]{i, dis.readUnsignedShort()};
				System.out.printf("Entry %d is a class with name index %d\n", i, entries[i - 1][1]);
				break;
			case 9: //CONSTANT_Fieldref
			case 10: //CONSTANT_Methodref
			case 11: //CONSTANT_InterfaceMethodref
				entries[i - 1] = new Object[]{i, dis.readUnsignedShort(), dis.readUnsignedShort()};
				System.out.printf("Entry %d is a field, method, or interface method (tag value %d) with class index %d and name/type index %d\n", i, tag, entries[i - 1][1], entries[i - 1][2]);
				break;
			case 8: //CONSTANT_String
				entries[i - 1] = new Object[]{i, dis.readUnsignedShort()};
				System.out.printf("Entry %d is a string with string index %d\n", i, entries[i - 1][1]);
				break;
			case 3: //CONSTANT_Integer
				entries[i - 1] = new Object[]{i, dis.readInt()};
				System.out.printf("Entry %d is an integer with value %d\n", i, entries[i - 1][1]);
				break;
			case 4: //CONSTANT_Float
				entries[i - 1] = new Object[]{i, dis.readFloat()};
				System.out.printf("Entry %d is a float with value %f\n", i, entries[i - 1][1]);
				break;
			case 5: //CONSTANT_Long
				entries[i - 1] = new Object[]{i, dis.readLong()};
				System.out.printf("Entry %d is a long with value %d\n", i, entries[i - 1][1]);
				i++;
				break;
			case 6: //CONSTANT_Double
				entries[i - 1] = new Object[]{i, dis.readDouble()};
				System.out.printf("Entry %d is a double with value %f\n", i, entries[i - 1][1]);
				i++;
				break;
			case 12: //CONSTANT_NameAndType
				entries[i - 1] = new Object[]{i, dis.readUnsignedShort(), dis.readUnsignedShort()};
				System.out.printf("Entry %d is a name/type with name index %d and descriptor index %d\n", i, entries[i - 1][1], entries[i - 1][2]);
				break;
			case 1: //CONSTANT_Utf8
				int length = dis.readUnsignedShort();
				byte[] bytes = new byte[length];
				dis.readFully(bytes, 0, length);
				entries[i - 1] = new Object[]{i, length, new String(bytes)};
				System.out.printf("Entry %d is a UTF8 string with length %d and value %s\n", i, length, new String(bytes));
				break;
			default:
				System.out.printf("Recieved garbage data for entry %d\n", i);
				break;
			}
		}

		int acc = dis.readUnsignedShort();
		System.out.printf("Access flags: 0x%x - ACC_PUBLIC = %b, ACC_FINAL = %b, ACC_SUPER = %b, ACC_INTERFACE = %b, ACC_ABSTRACT = %b\n", acc, (acc & 0x1) != 0, (acc & 0x10) != 0, (acc & 0x20) != 0, (acc & 0x200) != 0, (acc & 0x400) != 0);
	
		System.out.printf("This class: %s\n", entries[(int)entries[dis.readUnsignedShort() - 1][1] - 1][2]);
		System.out.printf("Super class: %s\n", entries[(int)entries[dis.readUnsignedShort() - 1][1] - 1][2]);
		
		int intfaceCount = dis.readUnsignedShort();
		System.out.printf("Class implements %d interface(s)\n", intfaceCount);
		
		for (int i = 0; i < intfaceCount; i++)
			System.out.printf(" Interface %d is %s\n", i + 1, entries[(int)entries[dis.readUnsignedShort() - 1][1] - 1][2]);
		
		int fieldCount = dis.readUnsignedShort();
		System.out.printf("Class contains %d field(s)\n", fieldCount);

		for (int i = 0; i < fieldCount; i++)
		{
			int tmp = 0;
			System.out.printf(" Field at index %d has access flags 0x%x, name index %d (with value %s), descriptor index %d (with value %s), contains %d attribute(s)\n", i + 1, dis.readUnsignedShort(), tmp = dis.readUnsignedShort(), entries[tmp - 1][2], tmp = dis.readUnsignedShort(), entries[tmp - 1][2], tmp = dis.readUnsignedShort());
			
			for (int j = 0; j < tmp; j++)
				System.out.println(getAttributeDescriptor(i, entries, dis, "  "));
		}
		
		int methodCount = dis.readUnsignedShort();
		System.out.printf("Class contains %d method(s)\n", methodCount);
		
		for (int i = 0; i < methodCount; i++) {
			int tmp = 0;
			System.out.printf(" Method at index %d has access flags 0x%x, name index %d (with value %s), descriptor index %d (with value %s), contains %d attribute(s)\n", i + 1, dis.readUnsignedShort(), tmp = dis.readUnsignedShort(), entries[tmp - 1][2], tmp = dis.readUnsignedShort(), entries[tmp - 1][2], tmp = dis.readUnsignedShort());
			
			for (int j = 0; j < tmp; j++)
				System.out.println(getAttributeDescriptor(i, entries, dis, "  "));
		}
		
		int attribCount = dis.readUnsignedShort();
		System.out.printf("Class contains %d attributes\n", attribCount);
		
		for (int i = 0; i < attribCount; i++)
			System.out.println(getAttributeDescriptor(i, entries, dis, "  "));
	}
	
	private static String getAttributeDescriptor(int i, Object[][] entries, DataInputStream dis, String pad) throws IOException
	{
		String toReturn = "";
		
		int len = dis.readShort();
		String name = (String)entries[len - 1][2];
		toReturn += String.format(pad + "Attribute at index %d has name index %d (value %s), length %d", i + 1, len, name, len = dis.readInt());
		
		switch(name)
		{
		case "SourceFile":
		case "ConstantValue":
			int tableIndex = dis.readUnsignedShort();
			toReturn += String.format(", value %s", ((Object)entries[tableIndex - 1][entries[tableIndex - 1].length - 1]).toString());
			break;
		case "Code":
			toReturn += String.format(", maximum stack size %d, maximum local variables %d, code length %d", dis.readUnsignedShort(), dis.readUnsignedShort(), len = dis.readInt());
			dis.skipBytes(len);
			toReturn += String.format(", exception table size %d ", len = dis.readUnsignedShort());
			dis.skipBytes(len * 8);
			System.out.println(toReturn);
			toReturn += String.format(", attribute count %d", len = dis.readUnsignedShort());
			for (int j = 0; j < len; j++)
				toReturn += "\n" + getAttributeDescriptor(j, entries, dis, pad + " ");
			break;
		case "Exceptions":
			toReturn += String.format(", exception count %d", len = dis.readUnsignedShort());
			for (int j = 0; j < len; j++)
				toReturn += String.format("\n%s Exception %d of type %s ", pad, j, entries[(int)entries[dis.readUnsignedShort() - 1][1] - 1][2]);
			break;
		case "InnerClasses":
			toReturn += String.format(", contains %d inner classes", len = dis.readUnsignedShort());
			int nameVal = 0;
			for (int j = 0; j < len; j++)
				toReturn += String.format("\n%s Inner class %d described by entry %d (named %s), contained in class at index %d (named %s), named by constant %d (value %s), access flags are 0x%x", pad, j + 1, nameVal = dis.readUnsignedShort(), entries[(int)entries[nameVal - 1][1] - 1][2], nameVal = dis.readUnsignedShort(), nameVal == 0 ? "null" : entries[(int)entries[nameVal - 1][1] - 1][2], nameVal = dis.readUnsignedShort(), nameVal == 0 ? "null" : entries[nameVal - 1][2], dis.readUnsignedShort());
			break;
		default:
			dis.skipBytes(len);
		}
		
		return toReturn;
	}
}
