package net.lotrek.dynarec.execute;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

import org.omg.CORBA.DefinitionKind;

import com.sun.xml.internal.ws.api.ha.HaInfo;

import net.lotrek.dynarec.execute.AppleAsm.InstructionAssembler;

public class AppleAdvAsm implements Assembler {

	public Class<? extends Processor> getProcessorType()
	{
		return APPLEDRCx64.class;
	}

	public void assemble(InputStream is, OutputStream os) throws IOException
	{
		Scanner scr = new Scanner(is);
		DataOutputStream dos = new DataOutputStream(os);

		boolean inQuotes = false;
		ArrayList<String> lines = new ArrayList<>();
		while(scr.hasNextLine())
		{
			String line = scr.nextLine().trim();
			if(!line.isEmpty())
			{
				if(inQuotes && ((line.indexOf("*/") < line.indexOf("/*") && line.indexOf("*/") != -1) || (line.indexOf("*/") != -1 && line.indexOf("/*") == -1)))
				{
					line = line.substring(line.indexOf("*/") + 2);
					inQuotes = false;
				}
				
				if(inQuotes)
					continue;
				
				if(line.replaceAll("[^*/]", "").length() / 2 % 2 == 0)
					line = line.replaceAll("\\/\\*[^*/]*\\*\\/", "");
				else
				{
					inQuotes = true;
					line = line.replaceAll("\\/\\*[^*/]*\\*\\/", "").replaceAll("\\/\\*.*", "");
				}
				
				line = line.replaceAll(";.*", "").trim();
				
				if(line.isEmpty())
					continue;
			}else
				continue;
			
			lines.add(line);
		}
		
		for (int i = 0; i < lines.size(); i++)
		{
			if(lines.get(i).startsWith("#mode"))
				switch(lines.get(i).split(" ")[1])
				{
					case "defines":
						i += generateDefines(i, lines, dos);
						break;
					case "text":
						i += generateText(i, lines, dos);
						break;
					case "data":
						i += generateData(i, lines, dos);
						break;
					default:
						scr.close();
						throw new RuntimeException(String.format("Assembly failed: unknown #mode suffix \"%s\" at line %d", lines.get(i).split(" ")[1], i + 1));
				}
			else
			{
				scr.close();
				throw new RuntimeException(String.format("Assembly failed: misplaced construct at line %d, found \"%s\" where a #mode directive was expected", i + 1, lines.get(i)));
			}
		}
		
		scr.close();
	}
	
	private HashMap<String, AsmStruct> defines = new HashMap<>();
	private int generateDefines(int ind, ArrayList<String> lines, DataOutputStream dos)
	{
		int indO = ++ind;
		
		while(ind < lines.size() && !lines.get(ind).startsWith("#mode"))
		{
			String structDef = lines.get(ind++);
			if(structDef.equals("}"))
				continue;
			ArrayList<String> structLines = new ArrayList<>();
			while(!lines.get(++ind).equals("}"))
			{
				structLines.add(lines.get(ind));
			}
			defines.put(structDef, new AsmStruct(structDef, structLines.toArray(new String[0])));
		}
		
		defines.forEach((s, x) -> System.out.println(s + " : " + x.getLength(defines)));
		
		return ind - indO;
	}
	
	private int generateText(int ind, ArrayList<String> lines, DataOutputStream dos)
	{
		int indO = ++ind;
		
		String toAsm = "";
		while(ind < lines.size() && !lines.get(ind).startsWith("#mode"))
		{
			if(lines.get(ind).contains("->"))
				toAsm += generateStructAccess(lines.get(ind));
			else if(lines.get(ind).contains("<-"))
				toAsm += generateStructSet(lines.get(ind));
			else
			{
				String line = lines.get(ind);
				
				for (AsmStruct struct : defines.values())
					line = line.replace("len(" + struct.name + ")", "" + struct.getLength(defines));
				
				toAsm += line + "\n";
			}
			
			ind++;
		}
		
		try {
			System.out.println(toAsm);
			new AppleAsm().assemble(new ByteArrayInputStream(toAsm.getBytes()), dos);
			new AppleAsm().assemble(new ByteArrayInputStream(toAsm.getBytes()), new FileOutputStream(new File("test.bin")));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return ind - indO;
	}
	
	private int generateData(int ind, ArrayList<String> lines, DataOutputStream dos)
	{
		return 0;
	}
	
	//TODO: allow for array access; use * to store pointers rather than values
	
	private String generateStructSet(String desc)
	{
		String toReturn = "";
		
		String resReg = desc.split("<\\-")[1].trim();
		String[] halves = desc.split("<\\-")[0].trim().split("\\.");
		
		toReturn += String.format("MOV r0, %s\r", halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase().startsWith("r") ? halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "") : "#" + halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", ""));
		if(halves[1].matches(".*\\[.*\\].*"))
		{
			
		}else
		{
			if(defines.containsKey(halves[0].replaceAll("\\[.*", "").trim()))
			{
				switch(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(halves[1]))
				{
				case "int":
					toReturn += String.format("LBTR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "double":
				case "long":
					toReturn += String.format("RTR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "short":
					toReturn += String.format("AND %s, %s, #65536\nLSL %s, %s, #16\n", resReg, resReg, resReg, resReg);
					toReturn += String.format("LBTR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "byte":
					toReturn += String.format("BTR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;

				}
			}else
				throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a defined structure", halves[0].replaceAll("\\[.*", "").trim()));
		}
		
		return toReturn;
	}
	
	private String generateStructAccess(String desc)
	{
		String toReturn = "";
		
		String resReg = desc.split("\\->")[1].trim();
		String[] halves = desc.split("\\->")[0].trim().split("\\.");
		
		toReturn += String.format("MOV r0, %s\r", halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase().startsWith("r") ? halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "") : "#" + halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", ""));
		if(halves[1].matches(".*\\[.*\\].*"))
		{
			
		}else
		{
			if(defines.containsKey(halves[0].replaceAll("\\[.*", "").trim()))
			{
				switch(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(halves[1]))
				{
				case "int":
					toReturn += String.format("HBFR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "double":
				case "long":
					toReturn += String.format("RFR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "short":
					toReturn += String.format("HBFR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					toReturn += String.format("ASR %s, %s, #16\nAND %s, %s, #65536\n", resReg, resReg, resReg, resReg);
					break;
				case "byte":
					toReturn += String.format("BFR %s, r0, #%d\n", resReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;

				}
			}else
				throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a defined structure", halves[0].replaceAll("\\[.*", "").trim()));
		}
		
		return toReturn;
	}

	private static class AsmStruct
	{
		public static final HashMap<String, Integer> typesToLengths = new HashMap<>();
		static {
			typesToLengths.put("int", 4);
			typesToLengths.put("long", 8);
			typesToLengths.put("short", 2);
			typesToLengths.put("byte", 1);
			typesToLengths.put("double", 8);
		}
		
		public String name;
		public ArrayList<String[]> varEntries = new ArrayList<>();
		
		public AsmStruct(String name, String[] body)
		{
			this.name = name;
			for (String entry : body)
			{
				varEntries.add(new String[]{
						entry.split(" ")[0],
						entry.split(" ")[1],
						entry.contains("=") ? entry.split("=")[1].trim() : ""
				});
			}
		}
		
		public int getLength(HashMap<String, AsmStruct> defines)
		{
			int size = 0;
			
			for (String[] entry : varEntries)
				size += getEntrySize(entry, defines);
			
			return size;
		}
		
		public int getEntrySize(String[] entry, HashMap<String, AsmStruct> defines)
		{
			if(!entry[0].matches(".*\\[.*\\]"))
				if(typesToLengths.containsKey(entry[0]))
					return typesToLengths.get(entry[0]);
				else if(defines.containsKey(entry[0]))
					return defines.get(entry[0]).getLength(defines);
				else
					throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a defined structure or primitive type", entry[0]));
			
			return 0;
		}
		
		public int getVarOffset(HashMap<String, AsmStruct> defines, String var)
		{
			int i = 0;
			while(i < varEntries.size() && !varEntries.get(i)[1].equals(var))
				i++;
			
			if(varEntries.get(i)[1].equals(var))
			{
				int siz = 0;
				for (int j = 0; j < i; j++)
					siz += getEntrySize(varEntries.get(j), defines);
				return siz;
			}
			
			throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a valid property of struct %s", var, name));
		}
		
		public String getVarType(String var)
		{
			int i = 0;
			while(i < varEntries.size() && !varEntries.get(i)[1].equals(var))
				i++;
			
			if(varEntries.get(i)[1].equals(var))
				return varEntries.get(i)[0];
			
			throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a valid property of struct %s", var, name));
		}
	}
	
	public static enum ASMInstructions
	{
		MOV(0, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			if(!is64)
			{
				if(!line[3].startsWith("#"))
					head = 1 << 15 | (Integer.parseInt(line[3].replace("R","")) & 0xf);
				else
					head = Integer.parseInt(line[3].replace("#", "")) & 0x7fff;
				dos.writeShort(head);
			}else
			{
				if(!line[3].startsWith("#"))
					dos.writeShort(1 << 15);
				else
					dos.writeShort(0);
				
				int toWrite = 0;
				if(!line[3].startsWith("#"))
					toWrite = (Integer.parseInt(line[3].replace("R","")) & 0xf);
				else
					toWrite = Integer.parseInt(line[3].replace("#", ""));
				dos.writeInt(toWrite);
			}
		}),
		ADD(1, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		SUB(2, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		MUL(3, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		DIV(4, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		ASR(5, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		LSL(6, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		LSR(7, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		IFD(8, (line, head, dos, is64)->{
			dos.writeShort(head);
			dos.writeShort(Integer.parseInt(line[2].replace("R","")) & 0xf);
		}),
		DFI(9, (line, head, dos, is64)->{
			dos.writeShort(head);
			dos.writeShort(Integer.parseInt(line[2].replace("R","")) & 0xf);
		}),
		DMOV(10, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[2].replace("R","")) & 0xf));
			dos.writeShort(0);
			dos.writeFloat(Float.parseFloat(line[3].replace("#","")));
		}),
		DADD(11, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[2].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[4].startsWith("#") ? 0 : 1));
			if(line[4].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[4].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[4].replace("R","")) & 0xf);
		}),
		DSUB(12, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[2].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[4].startsWith("#") ? 0 : 1));
			if(line[4].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[4].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[4].replace("R","")) & 0xf);
		}),
		DMUL(13, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[2].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[4].startsWith("#") ? 0 : 1));
			if(line[4].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[4].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[4].replace("R","")) & 0xf);
		}),
		DDIV(14, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[2].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[4].startsWith("#") ? 0 : 1));
			if(line[4].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[4].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[4].replace("R","")) & 0xf);
		}),
		RTR(15, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		RFR(16, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		BTR(17, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		BFR(18, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		HBTR(19, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		LBTR(20, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		HBFR(21, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		HLT(22, (line, head, dos, is64)->{
			dos.writeShort(head);
			dos.writeShort(0);
		}),
		B(23, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[4].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[5].replace("#","")) & 0x7) << 5) | (Integer.parseInt(line[3].replace("R","")) << 1 & 0xf) | (line[2].startsWith("#") ? 0 : 1));
			if(line[2].startsWith("#"))
				dos.writeInt(Integer.parseInt(line[2].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[2].replace("R","")) & 0xf);
		}),
		BL(24, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[4].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[5].replace("#","")) & 0x7) << 5) | (Integer.parseInt(line[3].replace("R","")) << 1 & 0xf) | (line[2].startsWith("#") ? 0 : 1));
			if(line[2].startsWith("#"))
				dos.writeInt(Integer.parseInt(line[2].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[2].replace("R","")) & 0xf);
		}),
		AND(25, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		ORR(26, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		XOR(27, (line, head, dos, is64)->{
			head |= Integer.parseInt(line[2].replace("R","")) & 0xf;
			dos.writeShort(head);
			head = (Integer.parseInt(line[3].replace("R","")) & 0xf) << 12;
			if(!line[4].startsWith("#"))
				head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
			else
				head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
			dos.writeShort(head);
		}),
		RET(28, (line, head, dos, is64)->{
			dos.writeShort(head);
			dos.writeShort(0);
		}),
		;
		
		private InstructionAssembler asm;
		private int id;
		private ASMInstructions(int id, InstructionAssembler impl)
		{
			asm = impl;
			this.id = id;
		}
		
		public void assemble(String[] line, int header, DataOutputStream dos, boolean is64) throws IOException
		{
			asm.assemble(line, header, dos, is64);
		}
		
		public int getId()
		{
			return id;
		}
	}
}
