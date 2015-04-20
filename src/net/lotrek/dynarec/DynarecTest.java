package net.lotrek.dynarec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import net.lotrek.dynarec.ClassGenerator.Bytecode;
import net.lotrek.dynarec.ClassGenerator.ConstPoolEntry;
import net.lotrek.dynarec.ClassGenerator.ConstPoolProvider;
import net.lotrek.dynarec.devices.InterruptController;
import net.lotrek.dynarec.devices.KeyboardDevice;
import net.lotrek.dynarec.devices.VideoDevice;
import net.lotrek.dynarec.execute.APPLEDRCx64;
import net.lotrek.dynarec.execute.APPLEDRCx64.InstructionWriter;
import net.lotrek.dynarec.execute.AppleAdvAsm;
import net.lotrek.dynarec.execute.Processor;
import net.lotrek.tools.MultiplexOutputStream;

public class DynarecTest
{
	public static final HashMap<String, String> globalKeys = new HashMap<>();
	
	public static void main(String[] args)
	{
		MultiplexOutputStream.logConsole("dynarec.log");
		
		Queue<String> argQueue = new LinkedBlockingQueue<>(Arrays.asList(args));
		processArgs(argQueue);
		
		System.setProperty("org.lwjgl.librarypath", new File("res/native/windows").getAbsolutePath());
		
		forceInit(ConstPoolEntry.class);
		forceInit(Bytecode.class);
		forceInit(ConstPoolProvider.class);
		forceInit(ClassGenerator.class);
		forceInit(APPLEDRCx64.class);
		forceInit(InstructionWriter.class);
		
		Processor proc = null;
		try {
			proc = (Processor) Class.forName(globalKeys.get("engine")).newInstance();
		} catch (Exception e1) {
			try {
				proc = new APPLEDRCx64(Integer.parseInt(globalKeys.getOrDefault("ram", "1024")), globalKeys.containsKey("bios") ? MultiplexOutputStream.readFully(new FileInputStream(new File(globalKeys.get("bios"))), true) : new byte[0], new VideoDevice(), new InterruptController(), new KeyboardDevice());
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		
		proc.startProcessor();
	}
	
	private static void processArgs(Queue<String> args)
	{
		while(!args.isEmpty())
		{
			String arg = args.poll();
			
			switch(arg)
			{
			case "--assemble":
				String asmFile = args.poll(), output = args.poll();
				try {
					new AppleAdvAsm().assemble(new FileInputStream(new File(asmFile)), new FileOutputStream(new File(output)));
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case "--engine":
				globalKeys.put("engine", args.poll());
				break;
			case "--bios":
				globalKeys.put("bios", args.poll());
				break;
			case "--ram":
				globalKeys.put("ram", args.poll());
				break;
			default:
				globalKeys.put(arg, "true");
			}
		}
	}
	
	public static <T> Class<T> forceInit(Class<T> klass)
	{
	    try {
	        Class.forName(klass.getName(), true, klass.getClassLoader());
	    } catch (ClassNotFoundException e) {
	        throw new AssertionError(e);  // Can't happen
	    }
	    
	    return klass;
	} 
}
