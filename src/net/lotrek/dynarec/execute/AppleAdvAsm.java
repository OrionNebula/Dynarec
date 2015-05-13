package net.lotrek.dynarec.execute;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
			{
				System.out.printf("Encountered section %s\n", lines.get(i).split(" ")[1]);
				switch(lines.get(i).split(" ")[1])
				{
					case "defines":
						i += generateDefines(i, lines, dos);
						break;
					case "data":
						i += generateData(i, lines, dos);
					case "text":
						i += generateText(i, lines, dos);
						break;
					default:
						scr.close();
						throw new RuntimeException(String.format("Assembly failed: unknown #mode suffix \"%s\" at line %d", lines.get(i).split(" ")[1], i + 1));
				}
			}
			else
			{
				scr.close();
				throw new RuntimeException(String.format("Assembly failed: misplaced construct at line %d, found \"%s\" where a #mode directive was expected", i + 1, lines.get(i)));
			}
		}
		
		scr.close();
	}
	
	private HashMap<String, String> variables = new HashMap<>();
	private String dataPostfix = "";
	private int generateData(int ind, ArrayList<String> lines, DataOutputStream dos)
	{
		int indO = ++ind;
		
		while(ind < lines.size() && !lines.get(ind).startsWith("#mode"))
		{
			String structDef = lines.get(ind++);
			System.out.printf("Defined variable %s with type %s\n", structDef.split(" ")[1], structDef.split(" ")[0]);
			variables.put(structDef.split(" ")[1], structDef.split(" ")[0]);
			dataPostfix += String.format(":V%sV:\n", structDef.split(" ")[1]);
			dataPostfix += String.format("[%d]\n", getTypeLength(structDef.split(" ")[0]));
		}
		
		return ind - indO;
	}
	
	private HashMap<String, AsmStruct> defines = new HashMap<>();
	{
		defines.put("L@int", new AsmStruct("L@int", new String[]{"int value"}));
		defines.put("L@long", new AsmStruct("L@long", new String[]{"long value"}));
		defines.put("L@byte", new AsmStruct("L@byte", new String[]{"byte value"}));
		defines.put("L@double", new AsmStruct("L@double", new String[]{"double value"}));
	}
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
				structLines.add(lines.get(ind));
			System.out.printf("Defined structure %s with content %s\n", structDef, structLines.toString());
			defines.put(structDef, new AsmStruct(structDef, structLines.toArray(new String[0])));
		}
		
		return ind - indO;
	}
	
	private int globOff = 0;
	private HashMap<String, Integer> labels = new HashMap<>();
	private int generateText(int ind, ArrayList<String> lines, DataOutputStream dos) throws IOException
	{
		int indO = ++ind;
		
		int loopLabel = 0;
		Stack<String> loopTags = new Stack<>();
		String toAsm = "";
		while(ind < lines.size() && !lines.get(ind).startsWith("#mode"))
		{
			try {
				if(lines.get(ind).equals("#data"))
					toAsm += dataPostfix + (dataPostfix = "");
				else if(lines.get(ind).contains("->"))
					toAsm += generateStructAccess(lines.get(ind));
				else if(lines.get(ind).contains("<-"))
					toAsm += generateStructSet(lines.get(ind));
				else if(lines.get(ind).startsWith("goto"))
					toAsm += String.format("64:B #%s, r0, r0, #0\n", lines.get(ind).replaceAll("goto\\s*", ""));
				else if(lines.get(ind).equals("end"))
					toAsm += loopTags.pop() + "\n";
				else if(lines.get(ind).startsWith("if"))
				{
					if(!lines.get(ind).contains("goto"))
					{
						String lbl = String.format(":L%dL:", loopLabel++);
						loopTags.push(lbl);
						
						String arg1 = lines.get(ind).replaceAll("\\s", "").split("!=|<=|>=|<|>|=")[0].replaceAll("if.*\\(|\\).*", ""),
								arg2 = lines.get(ind).replaceAll("\\s", "").split("!=|<=|>=|<|>|=")[1].replaceAll("if.*\\(|\\).*", ""),
								op = lines.get(ind).replaceAll(String.format("\\s|%s|%s|if.*\\(|\\).*", arg1, arg2), "");
						
						toAsm += String.format("64:B #%s, %s, %s,", lbl, arg1, arg2);;
						
						switch(op)
						{
						case "!=":
							toAsm += "#3\n";
							break;
						case "<=":
							toAsm += "#4\n";
							break;
						case ">=":
							toAsm += "#2\n";
							break;
						case "<":
							toAsm += "#6\n";
							break;
						case ">":
							toAsm += "#5\n";
							break;
						case "=":
							toAsm += "#1\n";
							break;
						default:
							throw new RuntimeException(String.format("Assembly failed: Invalid comparison operator \"^%s\"", op));
						}
						ind++;
						continue;
					}
					
					if(lines.get(ind).replaceAll("if.*\\(", "").replaceAll("\\).*", "").equals("true"))
					{
						toAsm += String.format("64:B #%s, r0, r0, #0\n", lines.get(ind).replaceAll("\\s", "").split("goto")[1].trim());
						ind++;
						continue;
					}
					
					String lbl = lines.get(ind).replaceAll("\\s", "").split("goto")[1].trim(),
							arg1 = lines.get(ind).replaceAll("\\s", "").split("!=|<=|>=|<|>|=")[0].replaceAll("if.*\\(|\\).*", ""),
							arg2 = lines.get(ind).replaceAll("\\s", "").split("!=|<=|>=|<|>|=")[1].replaceAll("if.*\\(|\\).*", ""),
							op = lines.get(ind).replaceAll(String.format("\\s|%s|%s|if.*\\(|\\).*", arg1, arg2), "");
					
					toAsm += String.format("64:B #%s, %s, %s,", lbl, arg1, arg2);
					
					switch(op)
					{
					case "!=":
						toAsm += "#1\n";
						break;
					case "<=":
						toAsm += "#5\n";
						break;
					case ">=":
						toAsm += "#6\n";
						break;
					case "<":
						toAsm += "#2\n";
						break;
					case ">":
						toAsm += "#4\n";
						break;
					case "=":
						toAsm += "#3\n";
						break;
					default:
						System.out.printf("%s %s %s\n", arg1, op, arg2);
						throw new RuntimeException(String.format("Assembly failed: Invalid comparison operator \"%s\"", op));
					}
				}
				else if(lines.get(ind).equals("do"))
				{
					String lbl = String.format(":L%dL:", loopLabel++);
					toAsm += lbl + "\n";
					loopTags.push(lbl);
				}else if(lines.get(ind).replaceAll("\\s", "").startsWith("while("))
				{
					if(loopTags.isEmpty())
						throw new RuntimeException("Assembly failed: orphaned \"while\"");
					
					if(lines.get(ind).replaceAll("while.*\\(", "").replaceAll("\\).*", "").equals("true"))
					{
						toAsm += String.format("64:B #%s, r0, r0, #0\n", loopTags.pop());
						ind++;
						continue;
					}
					
					String lbl = loopTags.pop(),
							arg1 = lines.get(ind).replaceAll("\\s", "").split("!=|<=|>=|<|>|=")[0].replaceAll("while.*\\(|\\).*", ""),
							arg2 = lines.get(ind).replaceAll("\\s", "").split("!=|<=|>=|<|>|=")[1].replaceAll("while.*\\(|\\).*", ""),
							op = lines.get(ind).replaceAll(String.format("\\s|%s|%s|while.*\\(|\\).*", arg1, arg2), "");
					
					toAsm += String.format("64:B #%s, %s, %s,", lbl, arg1, arg2);
					
					switch(op)
					{
					case "!=":
						toAsm += "#1\n";
						break;
					case "<=":
						toAsm += "#5\n";
						break;
					case ">=":
						toAsm += "#6\n";
						break;
					case "<":
						toAsm += "#2\n";
						break;
					case ">":
						toAsm += "#4\n";
						break;
					case "=":
						toAsm += "#3\n";
						break;
					default:
						throw new RuntimeException(String.format("Assembly failed: Invalid comparison operator \"^%s\"", op));
					}
				}
				else
				{
					String line = lines.get(ind);
					
					if(line.matches(".*sizeof\\(.*\\).*"))
					{
						String type = line.substring(line.indexOf("sizeof(") + "sizeof(".length(), line.indexOf(")"));
						line = line.replace(String.format("sizeof(%s)", type), "" + getTypeLength(type));
					}
					
					if(line.matches(".*0x[0-9A-Fa-f]{1,8}.*"))
					{
						Matcher hex = Pattern.compile("0x[0-9A-Fa-f]{1,8}").matcher(line);
						
						while(hex.find())
						{
							String num = hex.group();
							line = line.replace(num, "" + Integer.parseInt(num.substring(2), 16));
						}
					}
					
					for (AsmStruct struct : defines.values())
						line = line.replace("sizeof(" + struct.name + ")", "" + struct.getLength(defines));
					
					toAsm += line + "\n";
				}
			} catch (Exception e) {
				System.out.printf("Error at line \"%s\"\n", lines.get(ind));
				e.printStackTrace();
			}
			
			ind++;
		}
		
//		System.out.println(toAsm);
		
		int off = globOff;
		for (String line : toAsm.split("\n"))
		{
			line = line.trim();
			boolean is64Bit = line.startsWith("64:");
			if(is64Bit)
				line = line.substring(3).trim();
			
			if(line.matches(":.*:"))
			{
				labels.put(line, off);
				continue;
			}
			
			if(line.matches("\\[\\d\\]"))
			{
				off += Integer.parseInt(line.replaceAll("\\[|\\]", ""));
				continue;
			}
			
			off += is64Bit ? 8 : 4;
		}
		
		for (String line : toAsm.split("\n"))
		{
			line = line.trim();
			boolean is64Bit = line.startsWith("64:");
			if(is64Bit)
				line = line.substring(3).trim();
			
			line = line.toUpperCase().replace(",", " ").replaceAll(" +", " ");
			
			if(line.matches("\\[\\d\\]"))
			{
				dos.write(new byte[Integer.parseInt(line.replaceAll("\\[|\\]", ""))]);
				globOff += Integer.parseInt(line.replaceAll("\\[|\\]", ""));
				continue;
			}
			
			if(line.matches(":.*:"))
			{
				continue;
			}
			
			if(line.contains(":"))
				for (String label : labels.keySet())
					line = line.replace(label.toUpperCase(), "" + (labels.get(label) - globOff - (is64Bit ? 8 : 4)));	
		
			
			String[] seg = line.split(" ");
			String[] seg2 = new String[seg.length + 1];
			seg2[0] = seg[0];
			seg2[1] = "AL";
			System.arraycopy(seg, 1, seg2, 2, seg.length - 1);
			
			int toWrite = is64Bit ? 0x8 : 0;
			toWrite <<= 8;
			
			ASMInstructions asm;
			try {
				asm = ASMInstructions.valueOf(seg[0]);
			} catch (IllegalArgumentException e) {
				continue;
			}
			
			globOff += is64Bit ? 8 : 4;
			
			asm.assemble(seg2, (toWrite | asm.getId()) << 4, dos, is64Bit);
		}
		
		/*try {
			new AppleAsm().assemble(new ByteArrayInputStream(toAsm.getBytes()), dos);
		} catch (IOException e) {
			e.printStackTrace();
		}*/
		
		return ind - indO;
	}
	
	private String generateStructSet(String desc)
	{
		String toReturn = "";
		
		String resReg = desc.split("<\\-")[1].trim();
		
		//variable
		if(variables.containsKey(desc.split("<\\-")[0].trim().split("\\[|\\.")[0]))
		{
			String name = desc.split("<\\-")[0].trim().split("\\[|\\.")[0], type = variables.get(name);
			String toRender = " <- " + resReg;
			
			
			if(type.contains("["))
			{
				int lenOffset = getTypeLength(type.split("\\[")[0]) * Integer.parseInt(desc.split("<\\-")[0].trim().split("\\[|\\]")[1]);

				if(defines.containsKey(type.replaceAll("\\[.*", "")))
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[r0].value", type) + toRender;
				
				return String.format("ADD r0, r15, #:V%sV:\nADD r0, r0, #%d\n", name, lenOffset) + generateStructSet(toRender);
			}else
			{
				if(defines.containsKey(type))
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[r0].value", type) + toRender;
				
				return String.format("ADD r0, r15, #:V%sV:\n", name) + generateStructSet(toRender);
			}
		}
		
		String[] halves = desc.split("<\\-")[0].trim().split("\\.");

		//structure
		String addrReg = halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase().startsWith("r") ? halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "") : "r0";
		if(!halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase().startsWith("r"))
			toReturn += String.format("MOV r0, %s\n", "#" + halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", ""));
		if(halves[1].matches(".*\\[.*\\].*"))
		{
			String type = halves[1].split("\\[")[0].trim(), offsetVal = halves[1].replaceAll(".*\\[", "").replaceAll("\\].*", "").trim(), varIdent = halves[1].split("\\[")[0];
			
			offsetVal = offsetVal.startsWith("r") ? offsetVal : ("#" + offsetVal);
			
			if(offsetVal.startsWith("r"))
			{
				toReturn = String.format("64:MOV r0, #%d\n", getTypeLength(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(type).replaceAll("\\[|\\]", "")));
				toReturn += String.format("MUL r0, r0, %s\n", offsetVal);
			}else
				toReturn = String.format("64:MOV r0, #%d\n", getTypeLength(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(type).replaceAll("\\[|\\]", "")) * Integer.parseInt(halves[1].replaceAll(".*\\[", "").replaceAll("\\].*", "").trim()));
		
			toReturn += String.format("ADD r0, r0, %s\n", halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase());
			addrReg = "r0";
			
			if(defines.containsKey(halves[0].replaceAll("\\[.*", "").trim()))
			{
				switch(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(varIdent).replaceAll("\\[|\\]", ""))
				{
				case "int":
					toReturn += String.format("LBTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					break;
				case "double":
				case "long":
					toReturn += String.format("RTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					break;
				case "short":
					toReturn += String.format("AND %s, %s, #65536\nLSL %s, %s, #16\n", resReg, resReg, resReg, resReg);
					toReturn += String.format("LBTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					break;
				case "byte":
					toReturn += String.format("BTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					break;

				}
			}else
				throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a defined structure", halves[0].replaceAll("\\[.*", "").trim()));
			
		}else
		{
			if(defines.containsKey(halves[0].replaceAll("\\[.*", "").trim()))
			{
				switch(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(halves[1]))
				{
				case "int":
					toReturn += String.format("LBTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "double":
				case "long":
					toReturn += String.format("RTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "short":
					toReturn += String.format("AND %s, %s, #65536\nLSL %s, %s, #16\n", resReg, resReg, resReg, resReg);
					toReturn += String.format("LBTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "byte":
					toReturn += String.format("BTR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
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
		
		//variable
		if(variables.containsKey(desc.split("\\->")[0].trim().split("\\[|\\.")[0].replace("*", "")))
		{
			String name = desc.split("\\->")[0].trim().split("\\[|\\.")[0], type = variables.get(name.replace("*", ""));
			String toRender = " -> " + resReg;
			
			if(type.contains("[") && !name.contains("*"))
			{
				int lenOffset = getTypeLength(type.split("\\[")[0]) * Integer.parseInt(desc.split("<\\-")[0].trim().split("\\[|\\]")[1]);

				if(defines.containsKey(type.replaceAll("\\[.*", "")))
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[r0].value", type) + toRender;
				
				return String.format("ADD r0, r15, #:V%sV:\nADD r0, r0, #%d\n", name, lenOffset) + generateStructAccess(toRender);
			}else
			{
				String addrReg = "r0";
				boolean isPointer = name.contains("*");
				if(isPointer)
				{
					name = name.replace("*", "");
					addrReg = resReg;
				}
				
				if(defines.containsKey(type) && !isPointer)
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[r0].value", type) + toRender;
				
				return String.format("ADD %s, r15, #:V%sV:\n", addrReg, name) + (isPointer ? "" : generateStructAccess(toRender));
			}
		}
		
		String[] halves = desc.split("\\->")[0].trim().split("\\.");
		
		String addrReg = halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase().startsWith("r") ? halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "") : "r0";
		if(!halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase().startsWith("r"))
			toReturn += String.format("MOV r0, %s\n", "#" + halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", ""));
		if(halves[1].matches(".*\\[.*\\].*"))
		{
			String type = halves[1].split("\\[")[0].trim(), offsetVal = halves[1].replaceAll(".*\\[", "").replaceAll("\\].*", "").trim(), varIdent = halves[1].split("\\[")[0];
			
			offsetVal = offsetVal.startsWith("r") ? offsetVal : ("#" + offsetVal);
			
			if(offsetVal.startsWith("r"))
			{
				toReturn = String.format("64:MOV r0, #%d\n", getTypeLength(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(type).replaceAll("\\[|\\]", "")));
				toReturn += String.format("MUL r0, r0, %s\n", offsetVal);
			}else
				toReturn = String.format("64:MOV r0, #%d\n", getTypeLength(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(type).replaceAll("\\[|\\]", "")) * Integer.parseInt(halves[1].replaceAll(".*\\[", "").replaceAll("\\].*", "").trim()));
		
			toReturn += String.format("ADD r0, r0, %s\n", halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase());
			addrReg = "r0";
			
			if(defines.containsKey(halves[0].replaceAll("\\[.*", "").trim()))
			{
				switch(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(varIdent).replaceAll("\\[|\\]", ""))
				{
				case "int":
					toReturn += String.format("HBFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					break;
				case "double":
				case "long":
					toReturn += String.format("RFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					break;
				case "short":
					toReturn += String.format("HBFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					toReturn += String.format("ASR %s, %s, #16\nAND %s, %s, #65536\n", resReg, resReg, resReg, resReg);
					break;
				case "byte":
					toReturn += String.format("BFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, varIdent));
					break;

				}
			}else
				throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a defined structure", halves[0].replaceAll("\\[.*", "").trim()));
			
		}else
		{
			if(defines.containsKey(halves[0].replaceAll("\\[.*", "").trim()))
			{
				switch(defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarType(halves[1]))
				{
				case "int":
					toReturn += String.format("HBFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "double":
				case "long":
					toReturn += String.format("RFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;
				case "short":
					toReturn += String.format("HBFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					toReturn += String.format("ASR %s, %s, #16\nAND %s, %s, #65536\n", resReg, resReg, resReg, resReg);
					break;
				case "byte":
					toReturn += String.format("BFR %s, %s, #%d\n", resReg, addrReg, defines.get(halves[0].replaceAll("\\[.*", "").trim()).getVarOffset(defines, halves[1]));
					break;

				}
			}else
				throw new RuntimeException(String.format("Assembly failed: \"%s\" is not a defined structure", halves[0].replaceAll("\\[.*", "").trim()));
		}
		
		return toReturn;
	}

	private int getTypeLength(String type)
	{
		if(type.matches(".*\\[.*\\]"))
		{
			if(type.matches(".*\\[\\]"))
				return 0;
			return getTypeLength(type.split("\\[")[0])*Integer.parseInt(type.split("\\[|\\]")[1]);
		}
		
		if(defines.containsKey(type))
			return defines.get(type).getLength(defines);
		else if(AsmStruct.typesToLengths.containsKey(type))
			return AsmStruct.typesToLengths.get(type);
		
		throw new RuntimeException(String.format("Assembly failed: \"%s\" does not represent a valid type", type));
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
			try {
				asm.assemble(line, header, dos, is64);
			} catch (Exception e) {
				System.out.println("Error: " + Arrays.toString(line));
				e.printStackTrace();
			}
		}
		
		public int getId()
		{
			return id;
		}
	}
}
