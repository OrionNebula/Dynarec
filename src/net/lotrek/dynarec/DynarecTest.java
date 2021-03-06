package net.lotrek.dynarec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import net.lotrek.dynarec.ClassGenerator.Bytecode;
import net.lotrek.dynarec.ClassGenerator.ConstPoolEntry;
import net.lotrek.dynarec.ClassGenerator.ConstPoolProvider;
import net.lotrek.dynarec.devices.ACPIDevice;
import net.lotrek.dynarec.devices.DiskDevice;
import net.lotrek.dynarec.devices.InterruptController;
import net.lotrek.dynarec.devices.KeyboardDevice;
import net.lotrek.dynarec.devices.MouseDevice;
import net.lotrek.dynarec.devices.RealTimeClockDevice;
import net.lotrek.dynarec.devices.VideoDevice;
import net.lotrek.dynarec.execute.APPLEDRCx64;
import net.lotrek.dynarec.execute.APPLEDRCx64.InstructionWriter;
import net.lotrek.dynarec.execute.Assembler.AssemblyType;
import net.lotrek.dynarec.execute.Linker;
import net.lotrek.dynarec.execute.AppleAdvAsm;
import net.lotrek.dynarec.execute.Assembler;
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
		
		while(true)
		{
			Processor proc = null;
			try {
				proc = (Processor) Class.forName(globalKeys.get("engine")).newInstance();
			} catch (Exception e1) {
				try {
					proc = new APPLEDRCx64(Integer.parseInt(globalKeys.getOrDefault("ram", "1024")), globalKeys.containsKey("bios") ? MultiplexOutputStream.readFully(new FileInputStream(new File(globalKeys.get("bios"))), true) : new byte[0], new VideoDevice(), new InterruptController(), new KeyboardDevice(), new MouseDevice(), new RealTimeClockDevice(), new DiskDevice(new File("disk.bin")), new ACPIDevice());
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
			
			if(!proc.startProcessor())
				break;
			System.gc();
		}
	}
	
	private static void processArgs(Queue<String> args)
	{
		while(!args.isEmpty())
		{
			String arg = args.poll();
			
			switch(arg)
			{
			case "--link":
			{
				String outFile = args.poll(), mainFile = args.poll();
				int linkLen = Integer.parseInt(args.poll());
				InputStream[] fis = new InputStream[linkLen];
				for (int i = 0; i < fis.length; i++)
					try {
						fis[i] = new FileInputStream(new File(args.poll()));
					} catch (IOException e) {
						e.printStackTrace();
					}
				try {
					new Linker().link(new FileOutputStream(new File(outFile)), new FileInputStream(new File(mainFile)), fis);
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
			case "--assemble":
			{
				String asmFile = args.poll(), output = args.poll(), type = args.poll();
				try {
					System.out.printf("Began assembling \"%s\" to \"%s\"\n", new File(asmFile).getAbsolutePath(), new File(output).getAbsolutePath());
					new AppleAdvAsm().assemble(new FileInputStream(new File(asmFile)), new FileOutputStream(new File(output)), AssemblyType.valueOf(type));
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			}
			case "--adv-assemble":
			{
				String clazz = args.poll(), asmFile = args.poll(), output = args.poll(), type = args.poll();
				Object inst;
				try {
					inst = Class.forName(clazz).newInstance();
					System.out.println(inst instanceof Assembler ? "Attemping advanced assembly with \"" + inst.getClass().getName() + "\"" : "\"" + inst.getClass().getName() + "\" is not a valid Assembler; defaulting to AppleAdvAsm");
					((Assembler)(inst instanceof Assembler ? inst : new AppleAdvAsm())).assemble(new FileInputStream(new File(asmFile)), new FileOutputStream(new File(output)), AssemblyType.valueOf(type));
				} catch (InstantiationException | IllegalAccessException | ClassNotFoundException | IOException e1) {
					e1.printStackTrace();
				}
				break;
			}
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
