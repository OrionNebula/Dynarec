package net.lotrek.dynarec.execute;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppleCasm implements Assembler
{
	private static final HashMap<String, Class<?>> typeMaps = new HashMap<>();
	static
	{
		typeMaps.put("int", Integer.class);
		typeMaps.put("long", Long.class);
		typeMaps.put("char", Byte.class);
		typeMaps.put("float", Float.class);
		typeMaps.put("double", Double.class);
	}
	
	public Class<? extends Processor> getProcessorType()
	{
		return APPLEDRCx64.class;
	}

	public void assemble(InputStream is, OutputStream os) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Scanner scr = new Scanner(is);
		
		HashMap<String, String> methodMap = new HashMap<>();
		HashMap<String, TypeValuePair> constPool = new HashMap<>();
		boolean shouldAssemble = true;
		int braceDepth = 0;
		while(scr.hasNextLine())
		{
			String line = scr.nextLine().trim().replaceAll("\\s+(?=((\\\\[\\\\\"]|[^\\\\\"])*\"(\\\\[\\\\\"]|[^\\\\\"])*\")*(\\\\[\\\\\"]|[^\\\\\"])*$)", " ");
			
			if(line.isEmpty())
				continue;
			else if(line.startsWith("#"))
			{
				String data = line.substring(line.split(" ")[0].length()).trim();
				
				switch(line.split(" ")[0].substring(1).toLowerCase())
				{
				case "include":
					if(!shouldAssemble)
						break;
					
					break;
				case "define":
					if(!shouldAssemble)
						break;
					
					if(!data.contains("="))
					{
						constPool.put(data, null);
						break;
					}
					
					String[] elements = data.split("=");
					if(elements[1].trim().startsWith("\"") && elements[1].trim().endsWith("\"")) //string
						constPool.put(elements[0], new TypeValuePair(String.class, elements[1].trim().substring(1, elements[1].trim().length() - 2)));
					else if(!elements[1].trim().matches("\\D")) //int
						constPool.put(elements[0], new TypeValuePair(Integer.class, Integer.parseInt(elements[1].trim())));
					else if(!elements[1].trim().matches("[^\\dABCDEFx]")) //hex int
						constPool.put(elements[0], new TypeValuePair(Integer.class, Integer.parseUnsignedInt(elements[1].trim().substring(2), 16)));
					else if(!elements[1].trim().matches("[^\\.\\d]")) //double
						constPool.put(elements[0], new TypeValuePair(Double.class, Double.parseDouble(elements[1].trim())));
					else if(constPool.containsKey(elements[1].trim())) //constant
						constPool.put(elements[0], constPool.get(elements[1].trim()));
					
					System.out.println("Defined " + elements[0]);
					break;
				case "ifdef":
					shouldAssemble = !constPool.containsKey(data);
					break;
				case "ifndef":
					shouldAssemble = constPool.containsKey(data);
					break;
				case "endif":
					shouldAssemble = true;
					break;
				}
				
				continue;
			}
			
			if(!shouldAssemble)
				continue;
			
			if(line.endsWith(";") && typeMaps.containsKey(line.split(" ")[0])) //variable
			{
				
			}else if(typeMaps.containsKey(line.split(" ")[0]) && braceDepth == 0) //method
			{
				scr.nextLine();
				braceDepth++;
				
				System.out.println(assembleMethod(scr));
				
				braceDepth--;
			}
		}
		
		scr.close();
		
		new AppleAsm().assemble(new ByteArrayInputStream(baos.toByteArray()), os);
	}
	
	//Index 0 is asm, all others are value to replace with offset
	private String[] assembleMethod(Scanner scr)
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos);
		
		ArrayList<String> variables = new ArrayList<>();
		String line;
		while(!(line = scr.nextLine().trim().replaceAll("\\s+(?=((\\\\[\\\\\"]|[^\\\\\"])*\"(\\\\[\\\\\"]|[^\\\\\"])*\")*(\\\\[\\\\\"]|[^\\\\\"])*$)", " ")).equals("}"))
		{
			if(typeMaps.containsKey(line.split(" ")[0]) && line.endsWith(";") && line.contains("=")) //variable declaration
			{
				variables.add(line.split(" |=")[1]);
			}
		}
		
		return new String[]{new String(baos.toByteArray())};
	}
	
	public static String evaluateExpression(String expr)
	{
		boolean paren = expr.startsWith("(") && expr.endsWith(")");
		expr = (paren ? expr.substring(1, expr.length() - 1) : expr).replaceAll("\\s", "");
		
		Pattern p = Pattern.compile("\\(([^\\)]+)\\)");
		Matcher m = p.matcher(expr);
		
		while(m.find())
		{
			String g = m.group();
			expr = expr.replace(m.group(), evaluateExpression(g));
		}
		
		System.out.println(expr);
		if(!expr.matches(".*[^\\.\\d +\\-/*()].*")) //can be evaluated
		{
			expr = expr.replace("\\s", "");
			
			ArrayList<String> pairs = new ArrayList<>();
			Matcher exM = Pattern.compile("\\d+[+\\-*/](\\d+)").matcher(expr);
			if (exM.find())
			    do
			        pairs.add(exM.group());
			    while (exM.find(exM.start(1)));
			
			if(pairs.size() == 1)
			{
				String[] elem = pairs.get(0).split("[*/\\-+]");
				
				switch(pairs.get(0).split("\\d+")[1])
				{
				case "*":
					return "" + Double.parseDouble(elem[0]) * Double.parseDouble(elem[1]);
				case "/":
					return "" + Double.parseDouble(elem[0]) / Double.parseDouble(elem[1]);
				case "+":
					return "" + (Double.parseDouble(elem[0]) + Double.parseDouble(elem[1]));
				case "-":
					return "" + (Double.parseDouble(elem[0]) - Double.parseDouble(elem[1]));
				}
			}
			
			return evaluateExpression(pairs.get(0));
		}
		
		return paren ? "(" + expr + ")" : expr;
		
	}
	
	public static class TypeValuePair
	{
		public Class<?> type;
		public Object value;
		
		public <T> TypeValuePair(Class<T> type, T value)
		{
			this.type = type;
			this.value = value;
		}
	}
}
