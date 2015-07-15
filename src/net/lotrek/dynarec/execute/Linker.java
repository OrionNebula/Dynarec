package net.lotrek.dynarec.execute;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.lotrek.dynarec.execute.AppleAdvAsm.ObjectPair;
import net.lotrek.tools.MultiplexOutputStream;

public class Linker
{
	public void link(OutputStream os, InputStream mainCode, InputStream...links) throws IOException
	{
		DataOutputStream dos = new DataOutputStream(os);
		DataInputStream disMainCode = new DataInputStream(mainCode);
		if(disMainCode.readInt() != 0xACCEDEFF)
			throw new RuntimeException("Link failure: main code file has an invalid magic number");
		if(disMainCode.readByte() != 0)
			throw new RuntimeException("Link failure: main code file is of an unsupported version");
		
		DataInputStream[] disLinks = new DataInputStream[links.length];
		for (int i = 0; i < disLinks.length; i++)
		{
			disLinks[i] = new DataInputStream(links[i]);
			if(disLinks[i].readInt() != 0xACCEDEFF)
				throw new RuntimeException("Link failure: link file " + (i + 1) + " has an invalid magic number");
			if(disLinks[i].readByte() != 0)
				throw new RuntimeException("Link failure: link file " + (i + 1) + " is of an unsupported version");
		}
		
		Map<String, ObjectPair<DataInputStream, Integer>> symbols = new HashMap<>();
		{
			int mainLen = disMainCode.readByte();
			for (int i = 0; i < mainLen; i++)
			{
				byte[] s = new byte[disMainCode.readByte()];
				disMainCode.readFully(s);
				symbols.put(new String(s), new ObjectPair<DataInputStream, Integer>(disMainCode, disMainCode.readInt()));
			}
			
			for (DataInputStream dataInputStream : disLinks)
			{
				int linkLen = dataInputStream.readByte();
				for (int i = 0; i < linkLen; i++)
				{
					byte[] s = new byte[dataInputStream.readByte()];
					dataInputStream.readFully(s);
					symbols.put(new String(s), new ObjectPair<DataInputStream, Integer>(dataInputStream, dataInputStream.readInt()));
				}
			}
		}
		
		System.out.printf("Found exported symbols: %s\n", symbols.toString());
		
		Map<DataInputStream, ArrayList<ObjectPair<String, Integer>>> requiredSymbols = new HashMap<>();
		{
			requiredSymbols.put(disMainCode, new ArrayList<>());
			int mainLen = disMainCode.readByte();
			for (int i = 0; i < mainLen; i++)
			{
				byte[] s = new byte[disMainCode.readByte()];
				disMainCode.readFully(s);
				requiredSymbols.get(disMainCode).add(new ObjectPair<String, Integer>(new String(s), disMainCode.readInt()));
			}
			
			for (DataInputStream dataInputStream : disLinks)
			{
				requiredSymbols.put(dataInputStream, new ArrayList<>());
				int linkLen = dataInputStream.readByte();
				for (int i = 0; i < linkLen; i++)
				{
					byte[] s = new byte[dataInputStream.readByte()];
					dataInputStream.readFully(s);
					requiredSymbols.get(dataInputStream).add(new ObjectPair<String, Integer>(new String(s), dataInputStream.readInt()));
				}
			}
		}
		
		System.out.printf("Found symbols to import: %s\n", requiredSymbols.toString());
		
		int[] codeLengths = new int[links.length + 1], codeStartPoints = new int[codeLengths.length];
		codeLengths[0] = disMainCode.readInt();
		codeStartPoints[0] = 0;
		for (int i = 0; i < links.length; i++)
		{
			codeLengths[i + 1] = disLinks[i].readInt();
			codeStartPoints[i + 1] = codeStartPoints[i] + codeLengths[i];
		}
		
		for (ArrayList<ObjectPair<String, Integer>> list : requiredSymbols.values())
			list.sort((x,y) -> x.getTwo() - y.getTwo());
		
		{
			int totRead = 0;
			ArrayList<ObjectPair<String, Integer>> list = requiredSymbols.get(disMainCode);
			while(!list.isEmpty())
			{
				ObjectPair<String, Integer> item = list.remove(0);
				byte[] b = new byte[item.getTwo() - totRead];
				totRead += b.length;
				disMainCode.readFully(b);
				dos.write(b);
				DataInputStream symbolOwner = symbols.get(item.getOne()).getOne();
				int index = -1;
				for (int i = 0; i < disLinks.length; i++)
					if(symbolOwner == disLinks[i])
					{
						index = i + 1;
						break;
					}
				dos.writeInt(codeStartPoints[index] + symbols.get(item.getOne()).getTwo() - item.getTwo());
				disMainCode.skipBytes(4);
			}
			MultiplexOutputStream.writeInputToOutput(disMainCode, dos, false);
		}
		
		for (int i = 0; i < disLinks.length; i++)
		{
			int totRead = 0;
			ArrayList<ObjectPair<String, Integer>> list = requiredSymbols.get(disLinks[i]);
			while(!list.isEmpty())
			{
				ObjectPair<String, Integer> item = list.remove(0);
				byte[] b = new byte[item.getTwo() - totRead];
				totRead += b.length;
				disLinks[i].readFully(b);
				dos.write(b);
				DataInputStream symbolOwner = symbols.get(item.getOne()).getOne();
				int index = -1;
				if(symbolOwner == disMainCode)
					index = 0;
				else
					for (int j = 0; j < disLinks.length; j++)
						if(symbolOwner == disLinks[j])
						{
							index = j + 1;
							break;
						}
				dos.writeInt(codeStartPoints[index] + symbols.get(item.getOne()).getTwo() - item.getTwo() - codeStartPoints[i + 1]);
				disLinks[i].skipBytes(4);
			}
			MultiplexOutputStream.writeInputToOutput(disLinks[i], dos, false);
		}
	}
}
