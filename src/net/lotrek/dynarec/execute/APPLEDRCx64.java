package net.lotrek.dynarec.execute;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import net.lotrek.dynarec.ByteArrayClassLoader;
import net.lotrek.dynarec.ClassGenerator;
import net.lotrek.dynarec.ClassGenerator.Bytecode;
import net.lotrek.dynarec.ClassGenerator.ConstPoolEntry;
import net.lotrek.dynarec.devices.MemorySpaceDevice;
import net.lotrek.dynarec.devices.Register;

public class APPLEDRCx64 extends Processor
{
	//Maps mem address to Object[]{Object, Method}
	private HashMap<Integer, Object[]> compiledSegments = new HashMap<>();
	private static final int PC = 15, LR = 14, ProcMode = 13, IVT = 12;
	private long[][] registers = new long[2][16];
	private volatile boolean shouldTerm, isHalted;
	private int stackSize = 10, varSize = 10;
	private ByteArrayClassLoader bacl = new ByteArrayClassLoader();
	private HashMap<String, Integer> volatileKeys = new HashMap<>();
	private final Object lock = new Object();
	private LinkedBlockingQueue<Object[]> interruptQueue = new LinkedBlockingQueue<>();
	private final InstructionWriter[] proc = new InstructionWriter[]
	{
		(linst, gen) -> { //MOV
			int len = 0;
			
			if((linst >> 63 & 1) == 1) //64 bit
			{
				if(((linst >> 47) & 1) == 1)
				{
					len += loadRegisterBytecode(gen, (int) (linst & 0xf));
				}else
				{
					len += 3;
					gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xffffffff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xffffffff)));
					gen.addBytecode(Bytecode.i2l);
				}
				
				len += setRegisterFromStackBytecode(gen, (int) ((linst >> 48) & 0xf));
			}else
			{
				if(((linst >> 15) & 1) == 1)
				{
					len += loadRegisterBytecode(gen, (int) (linst & 0xf));
				}else
				{
					len += 3;
					gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0x7fff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0x7fff)));
					gen.addBytecode(Bytecode.i2l);
				}
				
				len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			}
			
			return len;
		},
		(linst, gen) -> { //ADD
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len++;
			gen.addBytecode(Bytecode.ladd);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //SUB
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len++;
			gen.addBytecode(Bytecode.lsub);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //MUL
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len++;
			gen.addBytecode(Bytecode.lmul);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //DIV
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len++;
			gen.addBytecode(Bytecode.ldiv);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //ASR
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
			{
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
				gen.addBytecode(Bytecode.l2i);
				len++;
			}
			else
			{
				len += 2;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
			}
			
			len++;
			gen.addBytecode(Bytecode.lushr);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //LSL
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
			{
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
				gen.addBytecode(Bytecode.l2i);
				len++;
			}
			else
			{
				len += 2;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
			}
			
			len++;
			gen.addBytecode(Bytecode.lshl);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //LSR
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
			{
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
				gen.addBytecode(Bytecode.l2i);
				len++;
			}
			else
			{
				len += 2;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
			}
			
			len++;
			gen.addBytecode(Bytecode.lshr);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //IFD
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int)(linst & 0xf));
			len += getDoubleFromLong(gen);
			gen.addBytecode(Bytecode.d2l);
			len += setRegisterFromStackBytecode(gen, (int)(linst & 0xf));
			
			return len;
		},
		(linst, gen) -> { //DFI
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int)(linst & 0xf));
			gen.addBytecode(Bytecode.l2d);
			len += getLongFromDouble(gen);
			len += setRegisterFromStackBytecode(gen, (int)(linst & 0xf));
			
			return len;
		},
		(linst, gen) -> { //DMOV
			int len = 3;
			
			int ind = getConstant(gen, "D" + (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)), ConstPoolEntry.CONSTANT_Double, (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)));
			gen.addBytecode(Bytecode.ldc2_w, ind >> 8, ind & 0xff);

			len += getLongFromDouble(gen);
			len += setRegisterFromStackBytecode(gen, (int)((linst >> 48) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //DADD
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int)(linst >> 36) & 0xf);
			len += getDoubleFromLong(gen);
			
			if(((linst >> 32) & 1) == 1)
			{
				len += loadRegisterBytecode(gen, (int)(linst & 0xf));
				len += getDoubleFromLong(gen);
			}
			else
			{
				len += 3;
				int ind = getConstant(gen, "D" + (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)), ConstPoolEntry.CONSTANT_Double, (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)));
				gen.addBytecode(Bytecode.ldc2_w, ind >> 8, ind & 0xff);
			}
			
			gen.addBytecode(Bytecode.dadd);

			len += getLongFromDouble(gen);
			len += setRegisterFromStackBytecode(gen, (int)((linst >> 40) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //DSUB
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int)(linst >> 36) & 0xf);
			len += getDoubleFromLong(gen);
			
			if(((linst >> 32) & 1) == 1)
			{
				len += loadRegisterBytecode(gen, (int)(linst & 0xf));
				len += getDoubleFromLong(gen);
			}
			else
			{
				len += 3;
				int ind = getConstant(gen, "D" + (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)), ConstPoolEntry.CONSTANT_Double, (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)));
				gen.addBytecode(Bytecode.ldc2_w, ind >> 8, ind & 0xff);
			}
			
			gen.addBytecode(Bytecode.dsub);

			len += getLongFromDouble(gen);
			len += setRegisterFromStackBytecode(gen, (int)((linst >> 40) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //DMUL
int len = 1;
			
			len += loadRegisterBytecode(gen, (int)(linst >> 36) & 0xf);
			len += getDoubleFromLong(gen);
			
			if(((linst >> 32) & 1) == 1)
			{
				len += loadRegisterBytecode(gen, (int)(linst & 0xf));
				len += getDoubleFromLong(gen);
			}
			else
			{
				len += 3;
				int ind = getConstant(gen, "D" + (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)), ConstPoolEntry.CONSTANT_Double, (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)));
				gen.addBytecode(Bytecode.ldc2_w, ind >> 8, ind & 0xff);
			}
			
			gen.addBytecode(Bytecode.dmul);

			len += getLongFromDouble(gen);
			len += setRegisterFromStackBytecode(gen, (int)((linst >> 40) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //DDIV
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int)(linst >> 36) & 0xf);
			len += getDoubleFromLong(gen);
			
			if(((linst >> 32) & 1) == 1)
			{
				len += loadRegisterBytecode(gen, (int)(linst & 0xf));
				len += getDoubleFromLong(gen);
			}
			else
			{
				len += 3;
				int ind = getConstant(gen, "D" + (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)), ConstPoolEntry.CONSTANT_Double, (double)Float.intBitsToFloat((int)(linst & 0xffffffffl)));
				gen.addBytecode(Bytecode.ldc2_w, ind >> 8, ind & 0xff);
			}
			
			gen.addBytecode(Bytecode.ddiv);

			len += getLongFromDouble(gen);
			len += setRegisterFromStackBytecode(gen, (int)((linst >> 40) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //RTR
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			gen.addBytecode(Bytecode.ladd);
			
			len += writeLongToMemory(gen);
			
			return len;
		},
		(linst, gen) -> { //RFR
			int len = 1;
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			gen.addBytecode(Bytecode.ladd);
			
			len += readLongFromMemory(gen);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //BTR
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			gen.addBytecode(Bytecode.ladd);
			
			len += writeByteToMemory(gen);
			
			return len;
		},
		(linst, gen) -> { //BFR
			int len = 1;
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			gen.addBytecode(Bytecode.ladd);
			
			len += readByteFromMemory(gen);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //HBTR
			int len = 4;
			
			len += loadRegisterBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			gen.addBytecode(Bytecode.ladd);
			gen.addBytecode(Bytecode.ldc, getConstant(gen, "I32", ConstPoolEntry.CONSTANT_Integer, 32));
			gen.addBytecode(Bytecode.lshr);
			
			len += writeHalfBytesToMemory(gen);
			
			return len;
		},
		(linst, gen) -> { //LBTR
			int len = 1;
			
			len += loadRegisterBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			gen.addBytecode(Bytecode.ladd);
			
			len += writeHalfBytesToMemory(gen);
			
			return len;
		},
		(linst, gen) -> { //HBFR
			int len = 1;
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			gen.addBytecode(Bytecode.ladd);
			
			len += readHalfBytesFromMemory(gen);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //HLT
			gen.addBytecode(Bytecode.iconst_4);
			gen.addBytecode(Bytecode.newarray, 11);
			
			gen.addBytecode(Bytecode.areturn);
			
			return 2;
		},
		(linst, gen) -> { //B
			int len = 1;
			
			gen.addBytecode(Bytecode.iconst_4);
			gen.addBytecode(Bytecode.newarray, 11);
			
			gen.addBytecode(Bytecode.dup);
			
			gen.addBytecode(Bytecode.iconst_0);
			
			if(((linst >> 32) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				int con = getConstant(gen, "I" + (int)(linst & 0xffffffff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xffffffff));
				gen.addBytecode(Bytecode.ldc, con);
				gen.addBytecode(Bytecode.i2l);
			}
			
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_1);
			len += loadRegisterBytecode(gen, (int)(linst >> 33 & 0xf));
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_2);
			len += loadRegisterBytecode(gen, (int)(linst >> 48 & 0xf));
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_3);
			
			int con = getConstant(gen, "I" + (int)((linst >> 37) & 0x7), ConstPoolEntry.CONSTANT_Integer, (int)((linst >> 37) & 0x7));
			gen.addBytecode(Bytecode.ldc, con);
			gen.addBytecode(Bytecode.i2l);
			
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.areturn);
			
			return len;
		},
		(linst, gen) -> { //BL
			int len = 1;
			
			gen.addBytecode(Bytecode.iconst_4);
			gen.addBytecode(Bytecode.newarray, 11);
			
			gen.addBytecode(Bytecode.dup);
			
			gen.addBytecode(Bytecode.iconst_0);
			
			if(((linst >> 32) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				int con = getConstant(gen, "I" + (int)(linst & 0xffffffff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xffffffff));
				gen.addBytecode(Bytecode.ldc, con);
				gen.addBytecode(Bytecode.i2l);
			}
			
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_1);
			len += loadRegisterBytecode(gen, (int)(linst >> 33 & 0xf));
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_2);
			len += loadRegisterBytecode(gen, (int)(linst >> 48 & 0xf));
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_3);
			
			int con = getConstant(gen, "I" + (int)((linst >> 37) & 0x7), ConstPoolEntry.CONSTANT_Integer, (int)((linst >> 37) & 0x7));
			gen.addBytecode(Bytecode.ldc, con);
			gen.addBytecode(Bytecode.i2l);
			
			gen.addBytecode(Bytecode.lastore);
			
			loadRegisterBytecode(gen, PC);
			setRegisterFromStackBytecode(gen, LR);
			
			gen.addBytecode(Bytecode.areturn);
			
			return len;
		},
		(linst, gen) -> { //AND
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len++;
			gen.addBytecode(Bytecode.land);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //ORR
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len++;
			gen.addBytecode(Bytecode.lor);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //XOR
			int len = 0;
			
			len += loadRegisterBytecode(gen, (int)((linst >> 12) & 0xf));
			
			if(((linst >> 11) & 1) == 1)
				len += loadRegisterBytecode(gen, (int) (linst & 0xf));
			else
			{
				len += 3;
				gen.addBytecode(Bytecode.ldc, getConstant(gen, "I" + (int)(linst & 0xfff), ConstPoolEntry.CONSTANT_Integer, (int)(linst & 0xfff)));
				gen.addBytecode(Bytecode.i2l);
			}
			
			len++;
			gen.addBytecode(Bytecode.lxor);
			len += setRegisterFromStackBytecode(gen, (int) ((linst >> 16) & 0xf));
			
			return len;
		},
		(linst, gen) -> { //RET
			int len = 0;
			
			gen.addBytecode(Bytecode.lconst_0);
			setRegisterFromStackBytecode(gen, ProcMode);
			
			gen.addBytecode(Bytecode.iconst_4);
			gen.addBytecode(Bytecode.newarray, 11);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_0);
			gen.addBytecode(Bytecode.lconst_1);
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_1);
			gen.addBytecode(Bytecode.lconst_0);
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_2);
			gen.addBytecode(Bytecode.lconst_1);
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.iconst_3);
			gen.addBytecode(Bytecode.iconst_3);
			gen.addBytecode(Bytecode.i2l);
			gen.addBytecode(Bytecode.lastore);
			
			gen.addBytecode(Bytecode.areturn);
			
			return len;
		},
	};
	
	public APPLEDRCx64(int memorySize, byte[] biosImage,
			MemorySpaceDevice...memorySpaceDevices) {
		super(memorySize, biosImage, memorySpaceDevices);
	}

	//TODO: Finish instruction set - condition codes
	protected void executeImpl()
	{
		while(!shouldTerm)
		{
			int addr = (int) getRegisters()[PC];
			int[] sh = hashCodeSegment(getMemory(), addr, 0xabec42eb);
			if(compiledSegments.containsKey(sh[0]))
				try {
//					registers[15] += sh[1];
					
					long[] ret = ((long[])((Method) compiledSegments.get(sh[0])[1]).invoke(compiledSegments.get(sh[0])[0], getRegisters(), getMemory()));
//					System.out.println(Arrays.toString(ret));
					if(ret[0] == 0)
					{
//						System.out.println("Cached HLT");
						
						getRegisters()[15] -= 4;
								
						setProcMode(0);
						
						synchronized (lock) {
							try {
								isHalted = true;
								lock.wait();
								if(shouldTerm)
									break;
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						
						isHalted = false;
						
						Object[] inter = interruptQueue.poll();
						Register toJump = new Register(Integer.class, (int) getRegisters()[IVT] + 4*(int)inter[0], this.getMemory());
						setProcMode(1);
						
//						System.arraycopy((long[])inter[1], 0, getRegisters(), 0, ((long[])inter[1]).length);
						getRegisters()[PC] = (int) toJump.getValue();
						
						continue;
					}
					
					switch((int)ret[3])
					{
					case 1:
						if(!(ret[1] != ret[2]))
							ret[0] = 0;
						break;
					case 2:
						if(!(ret[1] < ret[2]))
							ret[0] = 0;
						break;
					case 3:
						if(!(ret[1] == ret[2]))
							ret[0] = 0;
						break;
					case 4:
						if(!(ret[1] > ret[2]))
							ret[0] = 0;
						break;
					case 5:
						if(!(ret[1] <= ret[2]))
							ret[0] = 0;
						break;
					case 6:
						if(!(ret[1] >= ret[2]))
							ret[0] = 0;
						break;
					}
					
//					System.out.println("Cache " + ret[0] + " : " + addr + " : " + sh[0]);
					getRegisters()[PC] += ret[0];
					
					if(interruptQueue.size() > 0 && getRegisters()[ProcMode] == 0)
					{
						setProcMode(0);
						
						Object[] inter = interruptQueue.poll();
						Register toJump = new Register(Integer.class, (int) getRegisters()[IVT] + 4*(int)inter[0], this.getMemory());
						
						setProcMode(1);
						getRegisters()[PC] = (int) toJump.getValue();
					}
					
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					e.printStackTrace();
				}
			else
			try {
				volatileKeys.clear();
				ClassGenerator gen = new ClassGenerator("ClassGen0x" + Integer.toHexString(addr) + Integer.toHexString(sh[0]));

				for (int i = 0; i < sh[1];)
				{
					int inst = (int) Register.getTypeForBytes(Integer.class, getMemory(), addr + i);
					if(((inst >> 31) & 1) == 1) //64 bit
					{
						loadRegisterBytecode(gen, PC);
						gen.addBytecode(Bytecode.iconst_4);
						gen.addBytecode(Bytecode.iconst_4);
						gen.addBytecode(Bytecode.iadd);
						gen.addBytecode(Bytecode.i2l);
						gen.addBytecode(Bytecode.ladd);
						setRegisterFromStackBytecode(gen, PC);
						
						long linst = (long)Register.getTypeForBytes(Long.class, getMemory(), addr + i);
						proc[(int)(linst >> 52) & 0xff].writeInstruction(linst, gen);
						
						i += 8;
					}else
					{
						loadRegisterBytecode(gen, PC);
						gen.addBytecode(Bytecode.iconst_4);
						gen.addBytecode(Bytecode.i2l);
						gen.addBytecode(Bytecode.ladd);
						setRegisterFromStackBytecode(gen, PC);
						
						long linst = (long)inst & 0xffffffffl;
						proc[(int)(inst >> 20) & 0xff].writeInstruction(linst, gen);

						i += 4;
					}
				}
				
				gen.setMaxLocals(varSize);
				gen.setMaxStack(stackSize);
				byte[] b = gen.getClassData();
				//ClassReader.parseClass(new ByteArrayInputStream(b));
				Class<?> clazz = bacl.loadClass("ClassGen0x" + Integer.toHexString(addr) + Integer.toHexString(sh[0]), b, 0, b.length);
				compiledSegments.put(sh[0], new Object[]{clazz.newInstance(), clazz.getDeclaredMethods()[0]});
//				registers[15] += sh[1];
				long[] ret = ((long[])((Method) compiledSegments.get(sh[0])[1]).invoke(compiledSegments.get(sh[0])[0], getRegisters(), getMemory()));
//				System.out.println(Arrays.toString(ret));
				if(ret[0] == 0)
				{
					getRegisters()[15] -= 4;
//					System.out.println(Arrays.toString(getRegisters()));
					
					setProcMode(0);
					
//					System.out.println("New HLT");
					
					synchronized (lock) {
						try {
							isHalted = true;
							lock.wait();
							if(shouldTerm)
								break;
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					
					isHalted = false;
					
					Object[] inter = interruptQueue.poll();
					Register toJump = new Register(Integer.class, (int) getRegisters()[IVT] + 4*(int)inter[0], this.getMemory());
					setProcMode(1);
					getRegisters()[PC] = (int) toJump.getValue();
					
					continue;
				}
				
				switch((int)ret[3])
				{
				case 1:
					if(!(ret[1] != ret[2]))
						ret[0] = 0;
					break;
				case 2:
					if(!(ret[1] < ret[2]))
						ret[0] = 0;
					break;
				case 3:
					if(!(ret[1] == ret[2]))
						ret[0] = 0;
					break;
				case 4:
					if(!(ret[1] > ret[2]))
						ret[0] = 0;
					break;
				case 5:
					if(!(ret[1] <= ret[2]))
						ret[0] = 0;
					break;
				case 6:
					if(!(ret[1] >= ret[2]))
						ret[0] = 0;
					break;
				}
				
				System.out.println("New " + ret[0] + " : " + addr + " : " + sh[0]);
				getRegisters()[15] += ret[0];
				
				if(interruptQueue.size() > 0 && getRegisters()[ProcMode] == 0)
				{
					setProcMode(0);
					
					Object[] inter = interruptQueue.poll();
					Register toJump = new Register(Integer.class, (int) getRegisters()[IVT] + 4*(int)inter[0], this.getMemory());
					
					setProcMode(1);
					getRegisters()[PC] = (int) toJump.getValue();
				}
			} catch (ClassNotFoundException | IllegalAccessException
					| IllegalArgumentException
					| SecurityException | InstantiationException
					| IOException | InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println(Arrays.toString(getRegisters()));
	}
	
	protected void terminateImpl()
	{
		shouldTerm = true;
	}

	public void interrupt(int index, long...parameters)
	{
		try {
			interruptQueue.put(new Object[]{index, parameters});
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		synchronized (lock) {
			lock.notify();
		}
	}
	
	private long[] getRegisters()
	{
		return registers[(int) (registers[0][ProcMode] = registers[1][ProcMode])];
	}

	private void setProcMode(int mode)
	{
		registers[1][ProcMode] = mode;
	}
		
	public void setStackVarSize(int stack, int var)
	{
		stackSize = stack > stackSize ? stack : stackSize;
		varSize = var > varSize ? var : varSize;
	}

	private int getConstant(ClassGenerator gen, String key, ConstPoolEntry con, Object...args)
	{
		if(volatileKeys.containsKey(key))
			return volatileKeys.get(key);
		volatileKeys.put(key, gen.addConstPoolEntry(con, args));
		return getConstant(gen, key, con, args);
	}
	
	private int writeHalfBytesToMemory(ClassGenerator gen)
	{
		int toReturn = 2;
		setStackVarSize(8, 4);
		
		gen.addBytecode(Bytecode.l2i);
		gen.addBytecode(Bytecode.istore_3);
		
		for (int i = 1; i <= 4; i++)
		{
			gen.addBytecode(Bytecode.dup2);
			int con = getConstant(gen, "I" + (32 - 8*i), ConstPoolEntry.CONSTANT_Integer, 32 - 8*i);
			if(con <= 255)
				gen.addBytecode(Bytecode.ldc, con);
			else
			{
				toReturn++;
				gen.addBytecode(Bytecode.ldc_w, (con >> 8) & 0xff, con & 0xff);
			}
				
			gen.addBytecode(Bytecode.lshr);
			gen.addBytecode(Bytecode.l2i);
			gen.addBytecode(Bytecode.i2b);
			
			gen.addBytecode(Bytecode.aload_2);
			gen.addBytecode(Bytecode.dup_x1);
			gen.addBytecode(Bytecode.pop);
			
			gen.addBytecode(Bytecode.iload_3);
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.istore_3);
			gen.addBytecode(Bytecode.iinc, 3, 1);
			gen.addBytecode(Bytecode.dup_x1);
			gen.addBytecode(Bytecode.pop);
			
			gen.addBytecode(Bytecode.bastore);
			
			toReturn += 18;
		}
		
		gen.addBytecode(Bytecode.pop2);
		
		return toReturn;
	}
	
	private int readHalfBytesFromMemory(ClassGenerator gen)
	{
		int toReturn = 3;
		
		setStackVarSize(10, 4);
		
		gen.addBytecode(Bytecode.l2i);
		gen.addBytecode(Bytecode.istore_3);
		gen.addBytecode(Bytecode.lconst_0);
		
		for (int i = 1; i <= 4; i++)
		{
			gen.addBytecode(Bytecode.aload_2);
			gen.addBytecode(Bytecode.iload_3);
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.istore_3);
			gen.addBytecode(Bytecode.iinc, 3, 1);
			
			gen.addBytecode(Bytecode.baload); //load first byte
			gen.addBytecode(Bytecode.i2l);
			
			int con = getConstant(gen, "I" + 0xff, ConstPoolEntry.CONSTANT_Integer, 0xff);
			if(con <= 255)
				gen.addBytecode(Bytecode.ldc, con);
			else
			{
				toReturn++;
				gen.addBytecode(Bytecode.ldc_w, (con >> 8) & 0xff, con & 0xff);
			}
			
			gen.addBytecode(Bytecode.i2l);
			gen.addBytecode(Bytecode.land);
			
			con = getConstant(gen, "I" + (32 - 8*i), ConstPoolEntry.CONSTANT_Integer, 32 - 8*i);
			if(con <= 255)
				gen.addBytecode(Bytecode.ldc, con);
			else
			{
				toReturn++;
				gen.addBytecode(Bytecode.ldc_w, (con >> 8) & 0xff, con & 0xff);
			}
			
			gen.addBytecode(Bytecode.lshl);
			gen.addBytecode(Bytecode.lor);
			
			toReturn += 13;
		}
		
		return toReturn;
	}

	
	private int readLongFromMemory(ClassGenerator gen)
	{
		int toReturn = 3;
		
		setStackVarSize(10, 4);
		
		gen.addBytecode(Bytecode.l2i);
		gen.addBytecode(Bytecode.istore_3);
		gen.addBytecode(Bytecode.lconst_0);
		
		for (int i = 1; i <= 8; i++)
		{
			gen.addBytecode(Bytecode.aload_2);
			gen.addBytecode(Bytecode.iload_3);
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.istore_3);
			gen.addBytecode(Bytecode.iinc, 3, 1);
			
			gen.addBytecode(Bytecode.baload); //load first byte
			gen.addBytecode(Bytecode.i2l);
			
			int con = getConstant(gen, "I" + (64 - 8*i), ConstPoolEntry.CONSTANT_Integer, 64 - 8*i);
			if(con <= 255)
				gen.addBytecode(Bytecode.ldc, con);
			else
			{
				toReturn++;
				gen.addBytecode(Bytecode.ldc_w, (con >> 8) & 0xff, con & 0xff);
			}
				
			gen.addBytecode(Bytecode.lshl);
			gen.addBytecode(Bytecode.lor);
			
			toReturn += 13;
		}
		
		return toReturn;
	}
	
	private int readByteFromMemory(ClassGenerator gen)
	{
		setStackVarSize(10, 4);
		
		gen.addBytecode(Bytecode.l2i);
		gen.addBytecode(Bytecode.istore_3);
		
		gen.addBytecode(Bytecode.aload_2);
		gen.addBytecode(Bytecode.iload_3);
		
		gen.addBytecode(Bytecode.baload); //load first byte
		gen.addBytecode(Bytecode.i2l);
		
		return 6;
	}
	
	private int writeByteToMemory(ClassGenerator gen)
	{
		setStackVarSize(8, 4);
		
		gen.addBytecode(Bytecode.l2i);
		gen.addBytecode(Bytecode.istore_3);
		
		gen.addBytecode(Bytecode.l2i);
		gen.addBytecode(Bytecode.i2b);
		
		gen.addBytecode(Bytecode.aload_2);
		gen.addBytecode(Bytecode.dup_x1);
		gen.addBytecode(Bytecode.pop);
		
		gen.addBytecode(Bytecode.iload_3);
		gen.addBytecode(Bytecode.dup_x1);
		gen.addBytecode(Bytecode.pop);
		
		gen.addBytecode(Bytecode.bastore);
		
		return 11;
	}
	
	private int writeLongToMemory(ClassGenerator gen)
	{
		int toReturn = 2;
		
		setStackVarSize(8, 4);
		
		gen.addBytecode(Bytecode.l2i);
		gen.addBytecode(Bytecode.istore_3);
		
		for (int i = 1; i <= 8; i++)
		{
			gen.addBytecode(Bytecode.dup2);
			int con = getConstant(gen, "I" + (64 - 8*i), ConstPoolEntry.CONSTANT_Integer, 64 - 8*i);
			if(con <= 255)
				gen.addBytecode(Bytecode.ldc, con);
			else
			{
				toReturn++;
				gen.addBytecode(Bytecode.ldc_w, (con >> 8) & 0xff, con & 0xff);
			}
			
			gen.addBytecode(Bytecode.lshr);
			gen.addBytecode(Bytecode.l2i);
			gen.addBytecode(Bytecode.i2b);
			
			gen.addBytecode(Bytecode.aload_2);
			gen.addBytecode(Bytecode.dup_x1);
			gen.addBytecode(Bytecode.pop);
			
			gen.addBytecode(Bytecode.iload_3);
			gen.addBytecode(Bytecode.dup);
			gen.addBytecode(Bytecode.istore_3);
			gen.addBytecode(Bytecode.iinc, 3, 1);
			gen.addBytecode(Bytecode.dup_x1);
			gen.addBytecode(Bytecode.pop);
			
			gen.addBytecode(Bytecode.bastore);
			
			toReturn += 18;
		}
		
		gen.addBytecode(Bytecode.pop2);
		
		return toReturn;
	}
	
	private int getLongFromDouble(ClassGenerator gen)
	{
		int methodRef = getConstant(gen, "doubleToRawLongBits METHOD", ConstPoolEntry.CONSTANT_FieldMethodInterfaceMethodref, 10, getConstant(gen, "java/lang/Double CLASS", ConstPoolEntry.CONSTANT_Class, getConstant(gen, "java/lang/Double", ConstPoolEntry.CONSTANT_Utf8, "java/lang/Double")), getConstant(gen, "doubleToRawLongBits NAMETYPE", ConstPoolEntry.CONSTANT_NameAndType, getConstant(gen, "doubleToRawLongBits", ConstPoolEntry.CONSTANT_Utf8, "doubleToRawLongBits"), getConstant(gen, "(D)J", ConstPoolEntry.CONSTANT_Utf8, "(D)J")));
		gen.addBytecode(Bytecode.invokestatic, 0, methodRef);
		
		return 3;
	}
	
	private int getDoubleFromLong(ClassGenerator gen)
	{
		int methodRef = getConstant(gen, "longBitsToDouble METHOD", ConstPoolEntry.CONSTANT_FieldMethodInterfaceMethodref, 10, getConstant(gen, "java/lang/Double CLASS", ConstPoolEntry.CONSTANT_Class, getConstant(gen, "java/lang/Double", ConstPoolEntry.CONSTANT_Utf8, "java/lang/Double")), getConstant(gen, "longBitsToDouble NAMETYPE", ConstPoolEntry.CONSTANT_NameAndType, getConstant(gen, "longBitsToDouble", ConstPoolEntry.CONSTANT_Utf8, "longBitsToDouble"), getConstant(gen, "(J)D", ConstPoolEntry.CONSTANT_Utf8, "(J)D")));
		gen.addBytecode(Bytecode.invokestatic, 0, methodRef);
		
		return 3; 
	}
	
	//3 codes
	private int loadRegisterBytecode(ClassGenerator gen, int regindex)
	{
		int toReturn = 3;
		
		setStackVarSize(3, 2);
		
		gen.addBytecode(Bytecode.aload_1);
		
		switch(regindex)
		{
		case 0:
			gen.addBytecode(Bytecode.iconst_0);
			break;
		case 1:
			gen.addBytecode(Bytecode.iconst_1);
			break;
		case 2:
			gen.addBytecode(Bytecode.iconst_2);
			break;
		case 3:
			gen.addBytecode(Bytecode.iconst_3);
			break;
		case 4:
			gen.addBytecode(Bytecode.iconst_4);
			break;
		case 5:
			gen.addBytecode(Bytecode.iconst_5);
			break;
		default:
			toReturn++;
			int constInd = getConstant(gen, "I" + regindex, ConstPoolEntry.CONSTANT_Integer, regindex);
			if(constInd <= 255)
				gen.addBytecode(Bytecode.ldc, constInd);
			else
			{
				toReturn++;
				gen.addBytecode(Bytecode.ldc_w, (constInd >> 8) & 0xff, constInd & 0xff);
			}
			break;
		}
		
		gen.addBytecode(Bytecode.laload);
		
		return toReturn;
	}
	
	//5 codes
	private int setRegisterFromStackBytecode(ClassGenerator gen, int regindex)
	{
		int toReturn = 5;
		
		setStackVarSize(4, 5);
		
		gen.addBytecode(Bytecode.lstore_3);
		gen.addBytecode(Bytecode.aload_1);
		
		switch(regindex)
		{
		case 0:
			gen.addBytecode(Bytecode.iconst_0);
			break;
		case 1:
			gen.addBytecode(Bytecode.iconst_1);
			break;
		case 2:
			gen.addBytecode(Bytecode.iconst_2);
			break;
		case 3:
			gen.addBytecode(Bytecode.iconst_3);
			break;
		case 4:
			gen.addBytecode(Bytecode.iconst_4);
			break;
		case 5:
			gen.addBytecode(Bytecode.iconst_5);
			break;
		default:
			toReturn++;
			int constInd = getConstant(gen, "I" + regindex, ConstPoolEntry.CONSTANT_Integer, regindex);
			if(constInd <= 255)
				gen.addBytecode(Bytecode.ldc, constInd);
			else
			{
				toReturn++;
				gen.addBytecode(Bytecode.ldc_w, (constInd >> 8) & 0xff, constInd & 0xff);
			}
				break;
		}
		
		gen.addBytecode(Bytecode.lload_3);
		gen.addBytecode(Bytecode.lastore);
		
		return toReturn;
	}
	
	private static int[] hashCodeSegment(final byte[] data, int off, int seed)
	{
		int length = 0;
		par:
		while(true)
		{
			int inst = (int) Register.getTypeForBytes(Integer.class, data, off + length);

			if((inst >> 20 & 0xff) == 22 || (inst >> 20 & 0xff) == 23 || (inst >> 20 & 0xff) == 24 || (inst >> 20 & 0xff) == 28) //Will stop when encountering jump
			{
				length += (inst >> 20 & 0xff) == 23 ? 8 : 4;
				break par;
			}
			if(((inst >> 31) & 1) == 1) //64 bit
				length += 8;
			else
				length += 4;
		}
		
        // 'm' and 'r' are mixing constants generated offline.
        // They're not really 'magic', they just happen to work well.
        final int m = 0x5bd1e995;
        final int r = 24;

        // Initialize the hash to a random value
        int h = seed^length;
        int length4 = length/4;

        for (int i=0; i<length4; i++) {
            final int i4 = i*4;
            int k = (data[i4+0 + off]&0xff) +((data[i4+1 + off]&0xff)<<8)
                    +((data[i4+2 + off]&0xff)<<16) +((data[i4+3 + off]&0xff)<<24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }
        
        // Handle the last few bytes of the input array
        switch (length%4) {
        case 3: h ^= (data[(length&~3) +2 + off]&0xff) << 16;
        case 2: h ^= (data[(length&~3) +1 + off]&0xff) << 8;
        case 1: h ^= (data[length&~3 + off]&0xff);
                h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return new int[]{h, length};
    }
	
	public static interface InstructionWriter
	{
		public int writeInstruction(long inst, ClassGenerator gen);
	}
}
