package net.lotrek.dynarec.execute;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppleAdvAsm implements Assembler {

	public Class<? extends Processor> getProcessorType()
	{
		return APPLEDRCx64.class;
	}
	
	@Override
	public void assemble(InputStream is, OutputStream os, AssemblyType type) throws IOException
	{
		assemble(is, os, false, type);
	}
	
	private ArrayList<ObjectPair<String, Integer>> exportedSymbols = new ArrayList<>(), importedSymbols = new ArrayList<>();
	private void assemble(InputStream is, OutputStream os, boolean imp, AssemblyType type) throws IOException
	{
		Scanner scr = new Scanner(is);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);

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
				
				line = line.replaceAll("'.'", line.contains("'") ? ((int)line.split("'")[1].charAt(0)) + "" : "").replaceAll(";.*", "").trim();
				
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
						i += generateDefines(i, lines, dos, type);
						break;
					case "data":
						i += generateData(i, lines, dos, type);
						break;
					case "text":
						if(imp)
							throw new RuntimeException("Header files cannot contain text sections");
						i += generateText(i, lines, dos, type);
						break;
					case "macro":
						i += generateMacros(i, lines, dos, type);
						break;
					case "imports":
						i += generateImports(i, lines, dos, type);
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
		
		if(type == AssemblyType.ACCEDEFF)
		{
			DataOutputStream rDos = new DataOutputStream(os);
			rDos.writeInt(0xACCEDEFF);
			rDos.writeByte(0);
			rDos.writeByte(exportedSymbols.size());
			for (ObjectPair<String, Integer> symbol : exportedSymbols)
			{
				rDos.writeByte(symbol.one.length());
				rDos.write(symbol.one.getBytes());
				rDos.writeInt(symbol.two);
			}
			rDos.writeByte(importedSymbols.size());
			for (ObjectPair<String, Integer> symbol : importedSymbols)
			{
				rDos.writeByte(symbol.one.length());
				rDos.write(symbol.one.getBytes());
				rDos.writeInt(symbol.two);
			}
			rDos.writeInt(baos.size());
		}
		
		os.write(baos.toByteArray());
	}
	
	private int generateImports(int ind, ArrayList<String> lines, DataOutputStream dos, AssemblyType type) throws IOException
	{
		int indO = ++ind;
		
		while(ind < lines.size() && !lines.get(ind).startsWith("#mode"))
		{
			String importFile = lines.get(ind++).trim();
			System.out.printf("Imported header file \"%s\"\n", new File(importFile).getAbsolutePath());
			FileInputStream fis = new FileInputStream(new File(importFile));
			assemble(fis, dos, true, AssemblyType.FLAT_FILE);
			fis.close();
			if(!dataPostfix.isEmpty())
			{
				System.out.printf("Header file failed to toss #mode data declarations - tossing automatically\n");
				dataPostfix = "";
			}
		}
		
		return ind - indO;
	}
	
	private HashMap<String, ObjectPair<String, Boolean>> variables = new HashMap<>();
	private HashMap<String, ObjectPair<Class<? extends Number>, Number>> varValues = new HashMap<>();
	private String dataPostfix = "";
	private int generateData(int ind, ArrayList<String> lines, DataOutputStream dos, AssemblyType type)
	{
		int indO = ++ind;
		
		while(ind < lines.size() && !lines.get(ind).startsWith("#mode"))
		{
			String structDef = lines.get(ind++).replaceAll(" =.*", "");
			if(structDef.equals("#toss"))
			{
				dataPostfix = "";
				continue;
			}
			boolean isImported = false;
			if(structDef.startsWith("import int") && type == AssemblyType.ACCEDEFF)
			{
				System.out.printf("Expecting the import of symbol \"%s\"\n", structDef.split(" ")[2]);
				importedSymbols.add(new ObjectPair<String, Integer>(structDef.split(" ")[2], -1));
				structDef = structDef.replace("import ", "");
				isImported = true;
			}
			if(structDef.startsWith("extern") && type == AssemblyType.ACCEDEFF)
			{
				System.out.printf("Exporting symbol \"%s\"\n", structDef.split(" ")[2]);
				exportedSymbols.add(new ObjectPair<String, Integer>(structDef.split(" ")[2], -1));
				structDef = structDef.replace("extern ", "");
			}
			
			System.out.printf("Defined variable %s with type %s\n", structDef.split(" ")[1], structDef.split(" ")[0]);
			variables.put(structDef.split(" ")[1], new ObjectPair<String, Boolean>(structDef.split(" ")[0], isImported));
			dataPostfix += String.format(":V%sV:\n", structDef.split(" ")[1]);
			if(!lines.get(ind - 1).contains("="))
				dataPostfix += String.format("[%d]\n", getTypeLength(structDef.split(" ")[0]));
			else if(AsmStruct.typesToLengths.containsKey(structDef.split(" ")[0]))
			{
				structDef = lines.get(ind - 1);
				
				ObjectPair<Class<? extends Number>, Number> eval = evaluateExpression(structDef.split("=")[1].trim(), varValues);
				varValues.put(structDef.split(" ")[1], eval);
				System.out.printf("Assigned variable \"%s\" value %s\n", structDef.split(" ")[1], eval.two.toString());
				int intVal = 0;
				long longVal = 0;
				switch(structDef.split(" ")[0])
				{
				case "float":
					 intVal = Float.floatToRawIntBits(eval.two.floatValue());
				case "int":
					intVal = intVal == 0 ? eval.two.intValue() : intVal;
					for (int i = 3; i >= 0; i--)
						dataPostfix += String.format("{%s}\n", Integer.toString((intVal >> 8*i) & 0xff));
					break;
				case "double":
					 longVal = Double.doubleToRawLongBits(eval.two.doubleValue());
				case "long":
					longVal = longVal == 0 ? eval.two.longValue() : longVal;
					for (int i = 7; i >= 0; i--)
						dataPostfix += String.format("{%s}\n", Long.toString((longVal >> 8*i) & 0xff));
					break;
				case "short":
					short shortVal = eval.two.shortValue();
					for (int i = 1; i >= 0; i--)
						dataPostfix += String.format("{%s}\n", Integer.toString((shortVal >> 8*i) & 0xff));
					break;
				case "byte":
					dataPostfix += String.format("{%s}\n", Integer.toString((eval.two.byteValue()) & 0xff));
					break;
				}
			}
		}
		
		return ind - indO;
	}
	
	private HashMap<String, AsmMacro> macros = new HashMap<>();
	
	private int generateMacros(int ind, ArrayList<String> lines, DataOutputStream dos, AssemblyType type)
	{
		int indO = ++ind;
		
		while(ind < lines.size() && !lines.get(ind).startsWith("#mode"))
		{
			String structDef = lines.get(ind++);
			if(structDef.equals("}"))
				continue;
			String macroBody = "";
			while(!lines.get(++ind).equals("}"))
				macroBody += (lines.get(ind)) + "\n";
			System.out.printf("Defined macro %s with content:\n%s\n", structDef, macroBody);
			macros.put(structDef.split("\\(")[0], new AsmMacro(macroBody, structDef.split("\\(|\\)")[1].split(",")));
		}
		
		return ind - indO;
	}
	
	private static HashMap<String, AsmStruct> defines = new HashMap<>();
	static {
		defines.put("L@int", new AsmStruct("L@int", new String[]{"int value"}));
		defines.put("L@long", new AsmStruct("L@long", new String[]{"long value"}));
		defines.put("L@byte", new AsmStruct("L@byte", new String[]{"byte value"}));
		defines.put("L@double", new AsmStruct("L@double", new String[]{"double value"}));
	}
	
	private int generateDefines(int ind, ArrayList<String> lines, DataOutputStream dos, AssemblyType type)
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
	private int generateText(int ind, ArrayList<String> lines, DataOutputStream dos, AssemblyType asmType) throws IOException
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
				else if(lines.get(ind).matches(".*\\(.*\\).*") && macros.containsKey(lines.get(ind).trim().split("\\(")[0]))
				{
					String[] newLines = macros.get(lines.get(ind).trim().split("\\(")[0]).getMacroValue(lines.get(ind).trim().split("\\(|\\)")[1].split(",")).split("\n");
					lines.remove(ind);
					lines.addAll(ind--, Arrays.asList(newLines));
				}
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
							line = line.replace(num, "" + Integer.parseUnsignedInt(num.substring(2), 16));
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
				if(asmType == AssemblyType.ACCEDEFF && line.matches(":V.*V:"))
				{
					for (int i = 0; i < exportedSymbols.size(); i++)
						if((":V"+exportedSymbols.get(i).one+"V:").equals(line))
							exportedSymbols.set(i, new ObjectPair<String, Integer>(exportedSymbols.get(i).one, off));
					for (int i = 0; i < importedSymbols.size(); i++)
						if((":V"+importedSymbols.get(i).one+"V:").equals(line))
							importedSymbols.set(i, new ObjectPair<String, Integer>(importedSymbols.get(i).one, off));
				}
				
//				System.out.println("Added label \"" + line + "\" : " + labels);
				continue;
			}
			
			if(line.matches("\\[\\d+\\]"))
			{
				off += Integer.parseInt(line.replaceAll("\\[|\\]", ""));
				continue;
			}
			
			if(line.matches("\\{\\d+\\}"))
			{
				off += 1;
				continue;
			}
			
			if(line.matches("\".*\""))
			{
				off += line.trim().length() - 2;
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
			
			if(line.matches("\\[\\d+\\]"))
			{
				dos.write(new byte[Integer.parseInt(line.replaceAll("\\[|\\]", ""))]);
				globOff += Integer.parseInt(line.replaceAll("\\[|\\]", ""));
				continue;
			}
			
			if(line.matches("\\{\\d+\\}"))
			{
				dos.write(Integer.parseInt(line.replaceAll("\\{|\\}", "")));
				globOff += 1;
				continue;
			}
			
			if(line.matches("\".*\""))
			{
				dos.writeBytes(line.trim().substring(1, line.length() - 1));
				globOff += line.trim().length() - 2;
				continue;
			}
			
			
			if(line.matches(":.*:"))
			{
				continue;
			}
			
			if(line.contains(":"))
				for (String label : labels.keySet())
				{
					if(line.contains(label.toUpperCase()))
//						System.out.println("Replaced instance of \"" + label.toUpperCase() + "\"");
					line = line.replace(label.toUpperCase(), "" + (labels.get(label) - globOff - (is64Bit ? 8 : 4)));
				}
		
//			System.out.println(line + " : " + is64Bit);
			
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
		if(variables.containsKey(desc.split("<\\-")[0].trim().split("\\[|\\.")[0].replace("&", "")))
		{
			String name = desc.split("<\\-")[0].trim().split("\\[|\\.")[0], type = variables.get(name.replace("&", "")).one;
			String toRender = " <- " + resReg;
			
			if(variables.get(name.replace("&", "")).two)
				System.err.println("Warning: Setting imported variables does not have the expected effect; pointers may become corrupt");
			
			if(type.contains("["))
			{
				int lenOffset = getTypeLength(type.split("\\[")[0]) * Integer.parseInt(desc.split("<\\-")[0].trim().split("\\[|\\]")[1]);

				if(defines.containsKey(type.replaceAll("\\[.*", "")))
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[r0].value", type) + toRender;
				
				return String.format("64: ADD r0, r15, #:V%sV:\n64: ADD r0, r0, #%d\n", name, lenOffset) + generateStructSet(toRender);
			}else
			{
				boolean isRef = desc.contains("&");
				
				if(isRef)
					name = name.replace("&", "");
				
				if(defines.containsKey(type))
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[r0].value", type) + toRender;
				
//				System.out.println(desc);
//				System.out.println((isRef?"":String.format("64: ADD r0, r15, #:V%sV:\n", name)) + (isRef ? generateStructAccess(name + " -> r0") : "") + generateStructSet(toRender));
				
				return (isRef?"":String.format("64: ADD r0, r15, #:V%sV:\n", name)) + (isRef ? generateStructAccess(name + " -> r0") : "") + generateStructSet(toRender);
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
		
			toReturn += String.format("64: ADD r0, r0, %s\n", halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase());
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
		if(variables.containsKey(desc.split("\\->")[0].trim().split("\\[|\\.")[0].replace("*", "").replace("&", "")))
		{
			String name = desc.split("\\->")[0].trim().split("\\[|\\.")[0], type = variables.get(name.replace("*", "").replace("&", "")).one;
			String toRender = " -> " + resReg;
			
			if(type.contains("[") && !name.contains("*"))
			{
				int lenOffset = getTypeLength(type.split("\\[")[0]) * Integer.parseInt(desc.split("<\\-")[0].trim().split("\\[|\\]")[1]);

				if(defines.containsKey(type.replaceAll("\\[.*", "")))
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[r0].value", type) + toRender;
				
				return String.format("64: ADD r0, r15, #:V%sV:\n64: ADD r0, r0, #%d\n", name, lenOffset) + generateStructAccess(toRender);
			}else
			{
				String addrReg = "r0";
				boolean isPointer = name.contains("*"), isRef = name.contains("&");
				if(isPointer)
				{
					name = name.replace("*", "");
					addrReg = resReg;
				}else if(isRef && !variables.get(name.replace("*", "").replace("&", "")).two)
					name = name.replace("&", "");
				else if(!isRef && variables.get(name.replace("*", "").replace("&", "")).two)
					return String.format("64: ADD r0, r15, #:V%sV:\nHBFR %s, r0, #0\nADD %s, %s, r0\n", name.toUpperCase(), resReg, resReg, resReg);
				else if(isRef && variables.get(name.replace("*", "").replace("&", "")).two)
					return String.format("64: ADD r0, r15, #:V%sV:\nHBFR %s, r0, #0\nADD %s, %s, r0\nHBFR %s, %s, #0\n", name.replace("&", "").toUpperCase(), resReg, resReg, resReg, resReg, resReg);
				
				
				if(defines.containsKey(type) && !isPointer)
					toRender = String.format("%s[r0].%s", type, desc.split("\\.")[1]) + toRender;
				else
					toRender = String.format("L@%s[%s].value", type, isRef ? addrReg : "r0") + toRender;
				
//				System.out.println(desc);
//				System.out.println(String.format("64: ADD %s, r15, #:V%sV:\n", addrReg, name) + (!isRef ? "" : generateStructAccess(name + " -> r0")) + (isPointer && !isRef ? "" : generateStructAccess(toRender)));
				
				return String.format("64: ADD %s, r15, #:V%sV:\n", addrReg, name) + (!isRef ? "" : generateStructAccess(name + " -> r0")) + (isPointer && !isRef ? "" : generateStructAccess(toRender));
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
		
			toReturn += String.format("64: ADD r0, r0, %s\n", halves[0].replaceAll(".*\\[", "").replaceAll("\\].*", "").toLowerCase());
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
			if(!type.matches(".*\\[\\d+\\]"))
				return 0;
			return getTypeLength(type.split("\\[")[0])*Integer.parseInt(type.split("\\[|\\]")[1]);
		}
		
		if(defines.containsKey(type))
			return defines.get(type).getLength(defines);
		else if(AsmStruct.typesToLengths.containsKey(type))
			return AsmStruct.typesToLengths.get(type);
		
		throw new RuntimeException(String.format("Assembly failed: \"%s\" does not represent a valid type", type));
	}
	
	private static Map<Class<? extends Number>, Integer> typePrecidence = new HashMap<>();
	static {
		typePrecidence.put(Double.class, 0);
		typePrecidence.put(Float.class, 1);
		typePrecidence.put(Long.class, 2);
		typePrecidence.put(Integer.class, 3);
		typePrecidence.put(Short.class, 4);
		typePrecidence.put(Byte.class, 5);
		
	}
	
	public static ObjectPair<Class<? extends Number>, Number> evaluateExpression(String expr, Map<String, ObjectPair<Class<? extends Number>, Number>> variables)
	{
		expr = expr.replaceAll("\\s", "");
		if(variables != null)
		for (String key : variables.keySet())
			expr = expr.replace(key, variables.get(key).two.toString());
		
		List<ObjectPair<String, ObjectPair<Class<? extends Number>, Number>>> parenBlocks = new ArrayList<>();
		
		int parenDepth = 0, segStart = -1;
		for (int i = 0; i < expr.length(); i++)
		{
			if(expr.charAt(i) == '(')
				parenDepth++;
			if(expr.charAt(i) == ')')
				parenDepth--;
			if(parenDepth == 1 && segStart == -1)
				segStart = i;
			
			if(parenDepth == 0 && segStart > -1)
			{
				parenBlocks.add(new ObjectPair<String, AppleAdvAsm.ObjectPair<Class<? extends Number>,Number>>(expr.substring(segStart + 1, i), evaluateExpression(expr.substring(segStart + 1, i), variables)));
				segStart = -1;
			}
		}
		
		for (ObjectPair<String, ObjectPair<Class<? extends Number>, Number>> objectPair : parenBlocks)
			expr = expr.replace("("+objectPair.one+")", objectPair.two.two.toString());
		
		while(expr.contains("*") || expr.contains("/"))
		{
			int i = expr.contains("*") && expr.contains("/") ? (expr.indexOf("*") < expr.indexOf("/") ? expr.indexOf("*") : expr.indexOf("/")) : (expr.contains("*") ? expr.indexOf("*") : expr.indexOf("/")), j = i;
			String left = "", right = "";
			
			i--;
			while(i >= 0 && !"*/+-".contains(""+expr.charAt(i)))
				left = expr.charAt(i--) + left;
			
			i = j + 1;
			while(i < expr.length() && !"*/+-".contains(""+expr.charAt(i)))
				right += expr.charAt(i++);
			
			ObjectPair<Class<? extends Number>, Number> lNum = parseNumber(left), rNum = parseNumber(right), eval = applyOperationWithPromotion(lNum, rNum, expr.charAt(j) == '*' ? (x,y) -> x*y : (x,y) -> x/y);

			expr = expr.replace(left+expr.charAt(j)+right, eval.two.toString());
		}
		
		while(expr.contains("+") || expr.contains("-"))
		{
			int i = expr.contains("+") && expr.contains("-") ? (expr.indexOf("+") < expr.indexOf("-") ? expr.indexOf("+") : expr.indexOf("-")) : (expr.contains("+") ? expr.indexOf("+") : expr.indexOf("-")), j = i;
			String left = "", right = "";
			
			i--;
			while(i >= 0 && !"*/+-".contains(""+expr.charAt(i)))
				left = expr.charAt(i--) + left;
			
			i = j+1;
			while(i < expr.length() && !"*/+-".contains(""+expr.charAt(i)))
				right += expr.charAt(i++);
			
			ObjectPair<Class<? extends Number>, Number> lNum = parseNumber(left), rNum = parseNumber(right), eval = applyOperationWithPromotion(lNum, rNum, expr.charAt(j) == '+' ? (x,y) -> x+y : (x,y) -> x-y);

			expr = expr.replace(left+expr.charAt(j)+right, eval.two.toString());
		}
		
		return parseNumber(expr);
	}
	
	public static ObjectPair<Class<? extends Number>, Number> parseNumber(String num)
	{
		if(num.contains("."))
			return new ObjectPair<Class<? extends Number>, Number>(Double.class, Double.parseDouble(num));
		long lNum = Long.parseLong(num);
		if(lNum < Byte.MAX_VALUE && lNum > Byte.MIN_VALUE)
			return new ObjectPair<Class<? extends Number>, Number>(Byte.class, Byte.parseByte(num));
		if(lNum < Short.MAX_VALUE && lNum > Short.MIN_VALUE)
			return new ObjectPair<Class<? extends Number>, Number>(Short.class, Short.parseShort(num));
		if(lNum < Integer.MAX_VALUE && lNum > Integer.MIN_VALUE)
			return new ObjectPair<Class<? extends Number>, Number>(Integer.class, Integer.parseInt(num));
		return new ObjectPair<Class<? extends Number>, Number>(Long.class, Long.parseLong(num));
	}
	
	public static ObjectPair<Class<? extends Number>, Number> applyOperationWithPromotion(ObjectPair<Class<? extends Number>, Number> one, ObjectPair<Class<? extends Number>, Number> two, BiFunction<Double, Double, Double> func)
	{
		Class<? extends Number> highestClass = typePrecidence.get(one.one) < typePrecidence.get(two.one) ? one.one : two.one;

		try {
			return new ObjectPair<Class<? extends Number>, Number>(highestClass, (Number)(Number.class.getMethod(((Class<?>)highestClass.getField("TYPE").get(null)).getSimpleName().toLowerCase() + "Value").invoke(func.apply(one.two.doubleValue(), two.two.doubleValue()))));
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException | NoSuchFieldException e) {
			return null;
		}
	}
	
	public static class ObjectPair<A, B>
	{
		private A one;
		private B two;
		
		public ObjectPair(A one, B two)
		{
			this.one = one;
			this.two = two;
		}
		
		public A getOne()
		{
			return one;
		}
		
		public B getTwo()
		{
			return two;
		}

		@Override
		public String toString() {
			return "ObjectPair [one=" + one + ", two=" + two + "]";
		}
	}
	
	private static class AsmMacro
	{
		private String body;
		private String[] argNames;
		
		public AsmMacro(String body, String...argNames)
		{
			this.argNames = argNames;
			this.body = body;
		}
		
		public String getMacroValue(String...argValues)
		{
			String newBody = body;
			
			for(int i = 0; i < argValues.length; i++)
				newBody = newBody.replace(argNames[i].trim(), argValues[i].trim());
			
			return newBody;
		}
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
			if(!is64)
			{
				if(!line[4].startsWith("#"))
					head |= 1 << 11 | (Integer.parseInt(line[4].replace("R","")) & 0xf);
				else
					head |= Integer.parseInt(line[4].replace("#", "")) & 0x7ff;
				dos.writeShort(head);
			}else
			{
				int toWrite = 0;
				if(!line[4].startsWith("#"))
					toWrite = (Integer.parseInt(line[4].replace("R","")) & 0xf);
				else
					toWrite = Integer.parseInt(line[4].replace("#", ""));
				dos.writeShort(head | (!line[4].startsWith("#") ? 1 : 0));
				dos.writeInt(toWrite);
			}
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
			dos.writeShort(head | (Integer.parseInt(line[3].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[4].startsWith("#") ? 0 : 1));
			if(line[4].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[4].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[4].replace("R","")) & 0xf);
		}),
		DSUB(12, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[3].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[4].startsWith("#") ? 0 : 1));
			if(line[4].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[4].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[4].replace("R","")) & 0xf);
		}),
		DMUL(13, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[3].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[4].startsWith("#") ? 0 : 1));
			if(line[4].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[4].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[4].replace("R","")) & 0xf);
		}),
		DDIV(14, (line, head, dos, is64)->{
			dos.writeShort(head | (Integer.parseInt(line[3].replace("R","")) & 0xf));
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
		DLN(29, (line, head, dos, is64)->{
			dos.writeShort(head);// | (Integer.parseInt(line[2].replace("R","")) & 0xf));
			dos.writeShort(((Integer.parseInt(line[2].replace("R","")) & 0xf) << 8) | (line[3].startsWith("#") ? 0 : 1));
			if(line[3].startsWith("#"))
				dos.writeFloat(Float.parseFloat(line[3].replace("#","")));
			else
				dos.writeInt(Integer.parseInt(line[3].replace("R","")) & 0xf);
		}),
		SVC(30, (line, head, dos, is64)->{
			dos.writeShort(head);
			dos.writeShort(Integer.parseInt(line[2].replace("#","")));
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
	
	public static interface InstructionAssembler
	{
		public void assemble(String[] line, int header, DataOutputStream dos, boolean is64) throws IOException;
	}
}
