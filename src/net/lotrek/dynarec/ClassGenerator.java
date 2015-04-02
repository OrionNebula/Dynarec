package net.lotrek.dynarec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class ClassGenerator
{
	private ArrayList<Object[]> bytecodeList = new ArrayList<>();
	private ArrayList<Object[]> bytecodeConstPool = new ArrayList<>();
	private int modBytes, maxStack, maxLocals, nextPoolIndex, poolSizeMod;
	
	public ClassGenerator(String className)
	{
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Class, 2);
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, className);
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Class, 4);
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, "java/lang/Object");
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, "execute");
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, "()V");
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, "Code");
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, "<init>");
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, "SourceFile");
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, className + ".java");
		addConstPoolEntry(ConstPoolEntry.CONSTANT_FieldMethodInterfaceMethodref, 10,3,12);
		addConstPoolEntry(ConstPoolEntry.CONSTANT_NameAndType, 8,6);
		addConstPoolEntry(ConstPoolEntry.CONSTANT_Utf8, "([J[B)[J");
	}
	
	public void setMaxLocals(int locals)
	{
		this.maxLocals = locals;
	}
	
	public void setMaxStack(int max)
	{
		this.maxStack = max;
	}
	
	public int getBytecodeSize()
	{
		return bytecodeList.size();
	}
	
	public void addReverseBytecode(Bytecode toAdd, int offset, int...args)
	{
		if(args.length > toAdd.argCount())
			throw new IllegalArgumentException("Given args exceed instruction args");
		
		bytecodeList.add(bytecodeList.size() - offset, new Object[]{toAdd, args});
		modBytes += args.length + 1;
	}
	
	public void addBytecode(Bytecode toAdd, int...args)
	{
		if(args.length > toAdd.argCount())
			throw new IllegalArgumentException("Given args exceed instruction args");
		
		bytecodeList.add(new Object[]{toAdd, args});
		modBytes += args.length + 1;
	}
	
	public int addConstPoolEntry(ConstPoolEntry entry, Object...args)
	{
		if(entry == ConstPoolEntry.CONSTANT_Double || entry == ConstPoolEntry.CONSTANT_Long)
		{
			bytecodeConstPool.add(new Object[]{entry, args});
			nextPoolIndex +=2;
			poolSizeMod++;
			return nextPoolIndex - 1;
		}
		
		bytecodeConstPool.add(new Object[]{entry, args});
		
		return ++nextPoolIndex;
	}
	
	public byte[] getClassData() throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(baos);
		
		dos.writeInt(0xCAFEBABE); //u4 magic
		dos.writeInt(0x34); //u2-u2 major-minor_version
		
		dos.writeShort(bytecodeConstPool.size() + 1 + poolSizeMod);// + 13); //u2 constant_pool_count
		
		for (int i = 0; i < bytecodeConstPool.size(); i++)
			((ConstPoolEntry)bytecodeConstPool.get(i)[0]).writeEntry(dos, (Object[])bytecodeConstPool.get(i)[1]);
		
		dos.writeShort(0x21); //u2 access_flags
		dos.writeShort(1);
		dos.writeShort(3);
		dos.writeShort(0);
		dos.writeShort(0);
		dos.writeShort(2); //method count
	
		//init method
		dos.writeShort(0x1); //access flags
		dos.writeShort(8); //<init> constant
		dos.writeShort(6); //()V constant
		dos.writeShort(1); //atribute count
	
		//Code atribute
		dos.writeShort(7); //name index Code
		dos.writeInt(17); //attribute length
		dos.writeShort(1);
		dos.writeShort(1);
		dos.writeInt(5);
		
		dos.writeByte(Bytecode.aload_0.getOpcode());
		dos.writeByte(Bytecode.invokespecial.getOpcode());
		dos.writeShort(11);
		dos.writeByte(Bytecode.return_op.getOpcode());
		
		dos.writeShort(0); //exceptions
		dos.writeShort(0); //attributes
		
		//execute method
		dos.writeShort(0x1); //access flags
		dos.writeShort(5); //execute constant
		dos.writeShort(13); //([J)V constant
		dos.writeShort(1); //atribute count
	
		//Code atribute
		dos.writeShort(7); //name index Code
		dos.writeInt(12 + modBytes); //attribute length
		dos.writeShort(maxStack);
		dos.writeShort(maxLocals);
		dos.writeInt(modBytes);
		
		for (int i = 0; i < bytecodeList.size(); i++) {
			Object[] byteCode = bytecodeList.get(i);
			dos.writeByte(((Bytecode)byteCode[0]).getOpcode());
			for (int j = 0; j < ((int[])byteCode[1]).length; j++)
				dos.writeByte(((int[])byteCode[1])[j]);
		}
		
		dos.writeShort(0); //exceptions
		dos.writeShort(0); //attributes
		
		dos.writeShort(1);
		
		//SourceFile
		dos.writeShort(9);
		dos.writeInt(2);
		dos.writeShort(10);
		
		dos.flush();
		baos.flush();
		
		return baos.toByteArray();
	}
	
	public static interface ConstPoolProvider
	{
		public void writeEntry(DataOutputStream dos, Object...args) throws IOException;
	}
	
	public static enum ConstPoolEntry
	{
		CONSTANT_Class((dos, args) -> {
			dos.writeByte(7);
			dos.writeShort((int)args[0]);
		}),
		CONSTANT_FieldMethodInterfaceMethodref((dos, args) -> {
			dos.writeByte((int)args[0]);
			dos.writeShort((int)args[1]);
			dos.writeShort((int)args[2]);
		}),
		CONSTANT_String((dos, args) -> {
			dos.writeByte(8);
			dos.writeShort((int)args[0]);
		}),
		CONSTANT_Integer((dos, args) -> {
			dos.writeByte(3);
			dos.writeInt((int)args[0]);
		}),
		CONSTANT_Float((dos, args) -> {
			dos.writeByte(4);
			dos.writeFloat((float)args[0]);
		}),
		CONSTANT_Long((dos, args) -> {
			dos.writeByte(5);
			dos.writeLong((long)args[0]);
		}),
		CONSTANT_Double((dos, args) -> {
			dos.writeByte(6);
			dos.writeDouble((double)args[0]);
		}),
		CONSTANT_NameAndType((dos, args) -> {
			dos.writeByte(12);
			dos.writeShort((int)args[0]);
			dos.writeShort((int)args[1]);
		}),
		CONSTANT_Utf8((dos, args) -> {
			dos.writeByte(1);
			dos.writeShort(((String)args[0]).length());
			dos.write(((String)args[0]).getBytes());
		}),
		;
		
		private ConstPoolProvider prov;
		
		private ConstPoolEntry(ConstPoolProvider provider)
		{
			prov = provider;
		}
		
		public void writeEntry(DataOutputStream dos, Object...args) throws IOException
		{
			prov.writeEntry(dos, args);
		}
	}
	
	public static enum Bytecode
	{
		aaload(0x32),
		aastore(0x53),
		aconst_null(0x01),
		aload(0x19, 1),
		aload_0(0x2a),
		aload_1(0x2b),
		aload_2(0x2c),
		aload_3(0x2d),
		anewarray(0xbd, 2),
		areturn(0xb0),
		arraylength(0xbe),
		astore(0x3a, 1),
		astore_0(0x4b),
		astore_1(0x4c),
		astore_2(0x4d),
		astore_3(0x4e),
		athrow(0xbf),
		baload(0x33),
		bastore(0x54),
		bipush(0x10, 1),
		breakpoint(0xca),
		caload(0x34),
		castore(0x55),
		checkcast(0xc0, 2),
		d2f(0x90),
		d2i(0x8e),
		d2l(0x8f),
		dadd(0x63),
		daload(0x31),
		dastore(0x52),
		dcmpg(0x98),
		dcmpl(0x97),
		dconst_0(0x0e),
		dconst_1(0x0f),
		ddiv(0x6f),
		dload(0x18, 1),
		dload_0(0x26),
		dload_1(0x27),
		dload_2(0x28),
		dload_3(0x29),
		dmul(0x6b),
		dneg(0x77),
		drem(0x73),
		dreturn(0xaf),
		dstore(0x39, 1),
		dstore_0(0x47),
		dstore_1(0x48),
		dstore_2(0x49),
		dstore_3(0x4a),
		dsub(0x67),
		dup(0x59),
		dup_x1(0x5a),
		dup_x2(0x5b),
		dup2(0x5c),
		dup2_x1(0x5d),
		dup2_x2(0x5e),
		f2d(0x8d),
		f2i(0x8b),
		f2l(0x8c),
		fadd(0x62),
		faload(0x30),
		fastore(0x51),
		fcmpg(0x96),
		fcmpl(0x95),
		fconst_0(0x0b),
		fconst_1(0x0c),
		fconst_2(0x0d),
		fdiv(0x6e),
		fload(0x17, 1),
		fload_0(0x22),
		fload_1(0x23),
		fload_2(0x24),
		fload_3(0x25),
		fmul(0x6a),
		fneg(0x76),
		frem(0x72),
		freturn(0xae),
		fstore(0x38, 1),
		fstore_0(0x43),
		fstore_1(0x44),
		fstore_2(0x45),
		fstore_3(0x46),
		fsub(0x66),
		getfield(0xb4, 2),
		getstatic(0xb2, 2),
		goto_op(0xa7, 2),
		goto_w(0xc8, 4),
		i2b(0x91),
		i2c(0x92),
		i2d(0x87),
		i2f(0x86),
		i2l(0x85),
		i2s(0x93),
		iadd(0x60),
		iaload(0x2e),
		iand(0x7e),
		iastore(0x4f),
		iconst_m1(0x2),
		iconst_0(0x3),
		iconst_1(0x4),
		iconst_2(0x5),
		iconst_3(0x6),
		iconst_4(0x7),
		iconst_5(0x8),
		idiv(0x6c),
		if_acmpeq(0xa5, 2),
		if_acmpne(0xa6, 2),
		if_icmpeq(0x9f, 2),
		if_icmpge(0xa2, 2),
		if_icmpgt(0xa3, 2),
		if_icmple(0xa4, 2),
		if_icmplt(0xa1, 2),
		if_icmpne(0xa0, 2),
		ifeq(0x99, 2),
		ifge(0x9c, 2),
		ifgt(0x9d, 2),
		ifle(0x9e, 2),
		iflt(0x9b, 2),
		ifne(0x9a, 2),
		ifnonnull(0xc7, 2),
		ifnull(0xc6, 2),
		iinc(0x84, 2),
		iload(0x15, 1),
		iload_0(0x1a),
		iload_1(0x1b),
		iload_2(0x1c),
		iload_3(0x1d),
		impdep1(0xfe),
		impdep2(0xff),
		imul(0x68),
		ineg(0x74),
		instanceof_op(0xc1, 2),
		invokedynamic(0xba, 4),
		invokeinterface(0xb9, 4),
		invokespecial(0xb7, 2),
		invokestatic(0xb8, 2),
		invokevirtual(0xb6, 2),
		ior(0x80),
		irem(0x70),
		ireturn(0xac),
		ishl(0x78),
		ishr(0x7a),
		istore(0x36, 1),
		istore_0(0x3b),
		istore_1(0x3c),
		istore_2(0x3d),
		istore_3(0x3e),
		isub(0x64),
		iushr(0x7c),
		ixor(0x82),
		jsr(0xa8, 2),
		jsr_w(0xc9, 4),
		l2d(0x8a),
		l2f(0x89),
		l2i(0x88),
		ladd(0x61),
		laload(0x2f),
		land(0x7f),
		lastore(0x50),
		lcmp(0x94),
		lconst_0(0x9),
		lconst_1(0x0a),
		ldc(0x12, 1),
		ldc_w(0x13, 2),
		ldc2_w(0x14, 2),
		ldiv(0x6d),
		lload(0x16, 1),
		lload_0(0x1e),
		lload_1(0x1f),
		lload_2(0x20),
		lload_3(0x21),
		lmul(0x69),
		lneg(0x75),
		lookupswitch(0xab, 4),
		lor(0x81),
		lrem(0x71),
		lreturn(0xad),
		lshl(0x79),
		lshr(0x7b),
		lstore(0x37, 1),
		lstore_0(0x3f),
		lstore_1(0x40),
		lstore_2(0x41),
		lstore_3(0x42),
		lsub(0x65),
		lushr(0x7d),
		lxor(0x83),
		monitorenter(0xc2),
		monitorexit(0xc3),
		multianewarray(0xc5, 3),
		new_op(0xbb, 2),
		newarray(0xbc, 1),
		nop(0x0),
		pop(0x57),
		pop2(0x58),
		putfield(0xb5, 2),
		putstatic(0xb3, 2),
		ret(0xa9, 1),
		return_op(0xb1),
		saload(0x35),
		sastore(0x56),
		sipush(0x11, 2),
		swap(0x5f),
		tableswitch(0xaa, 4),
		wide(0xc4, 3),

		;
		
		private int opcode, argCount;
		
		private Bytecode(int opcode)
		{
			this(opcode, 0);
		}
		
		Bytecode(int opcode, int argCount)
		{
			this.opcode = opcode & 0xff;
			this.argCount = argCount;
		}
		
		public int getOpcode()
		{
			return opcode;
		}
		
		public int argCount()
		{
			return argCount;
		}
	}
}
