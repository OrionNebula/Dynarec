package net.lotrek.dynarec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import net.lotrek.dynarec.ClassGenerator.Bytecode;
import net.lotrek.dynarec.ClassGenerator.ConstPoolEntry;
import net.lotrek.dynarec.ClassGenerator.ConstPoolProvider;
import net.lotrek.dynarec.devices.InterruptController;
import net.lotrek.dynarec.devices.VideoDevice;
import net.lotrek.dynarec.execute.APPLEDRCx64;
import net.lotrek.dynarec.execute.AppleCasm;
import net.lotrek.dynarec.execute.APPLEDRCx64.InstructionWriter;
import net.lotrek.dynarec.execute.AppleAsm;

public class DynarecTest
{
	public static void main(String[] args)
	{
		System.setProperty("org.lwjgl.librarypath", new File("res/native/windows").getAbsolutePath());
		
		forceInit(ConstPoolEntry.class);
		forceInit(Bytecode.class);
		forceInit(ConstPoolProvider.class);
		forceInit(ClassGenerator.class);
		forceInit(APPLEDRCx64.class);
		forceInit(InstructionWriter.class);
		
		/*try {
			System.out.println(AppleCasm.evaluateExpression("1024 - (7 * i)"));
			new AppleCasm().assemble(new FileInputStream(new File("test.c")), new ByteArrayOutputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
		}*/
		
		APPLEDRCx64 drc = new APPLEDRCx64(1024, new byte[0], new VideoDevice(), new InterruptController());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			new AppleAsm().assemble(new FileInputStream(new File("test.asm")), baos);
			byte[] b = baos.toByteArray();
			System.arraycopy(b, 0, drc.getMemory(), 0, b.length);
		} catch (IOException e) {
			e.printStackTrace();
		}
		long t = System.currentTimeMillis();
		drc.startProcessor();
		System.out.println(System.currentTimeMillis() - t);
	}
	
	public static <T> Class<T> forceInit(Class<T> klass) {
	    try {
	        Class.forName(klass.getName(), true, klass.getClassLoader());
	    } catch (ClassNotFoundException e) {
	        throw new AssertionError(e);  // Can't happen
	    }
	    return klass;
	} 
}
