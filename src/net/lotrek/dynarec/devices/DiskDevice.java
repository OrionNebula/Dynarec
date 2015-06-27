package net.lotrek.dynarec.devices;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;


public class DiskDevice extends MemorySpaceDevice
{
	public static final int BLOCK_LENGTH = 512;
	/*
	 * 0 byte diskCt --total disk count
	 * 1 int ringAddr --instruction ring address
	 * 2 byte ringLength --length of ring in instructions
	 */
	private Structure ctrlStruct = new Structure(Byte.class, Integer.class, Byte.class),
			/*
			 * 0 byte mode --0 skip execution, 1 request metadata, 2 write, 3 read
			 * 1 byte diskId
			 * 2 long diskSector
			 * 3 int memAddress
			 */
			cmdStruct = new Structure(Byte.class, Byte.class, Long.class, Integer.class),
			/*
			 * 0 int sector size
			 * 1 int sector count
			 * 2 int disk UID
			 */
			metaStruct = new Structure(Integer.class, Integer.class, Integer.class);
	private Register[] instance;
	private File[] diskFiles;
	private RandomAccessFile[] diskRAFs;
	private int ringIndex = 0;
	
	@SuppressWarnings("resource")
	public DiskDevice(File...diskFiles)
	{
		this.diskFiles = diskFiles;
		this.diskRAFs = new RandomAccessFile[diskFiles.length];
		int i = 0;
		for (File file : diskFiles) {
			try {
				RandomAccessFile raf = new RandomAccessFile(file, "rw");
				diskRAFs[i++] = raf;
				
				if(raf.length() % BLOCK_LENGTH != 0)
					raf.setLength(raf.length() + (raf.length() % BLOCK_LENGTH != 0 ? BLOCK_LENGTH - raf.length() % BLOCK_LENGTH : 0));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public int getOccupationLength()
	{
		return ctrlStruct.getLength();
	}

	public void executeDeviceCycle()
	{
		try {
			
			if(instance[2].getValue(Byte.class) > 0)
			{
				Register[] com = cmdStruct.getInstance(instance[1].getValue(Integer.class) + cmdStruct.getLength() * ringIndex, this.getProcessor().getMemory());
				RandomAccessFile raf = diskRAFs[com[1].getValue(Byte.class)];
				File file = diskFiles[com[1].getValue(Byte.class)];
				Byte sw = com[0].getValue(Byte.class);
				com[0].setValue(Byte.class, (byte)0);
				switch(sw)
				{
				case 1:
					Register[] meta = metaStruct.getInstance(com[3].getValue(Integer.class), this.getProcessor().getMemory());
					meta[0].setValue(Integer.class, BLOCK_LENGTH);
					meta[1].setValue(Integer.class, (int)(raf.length() / BLOCK_LENGTH));
					meta[2].setValue(Integer.class, file.getAbsolutePath().hashCode());
					break;
				case 2: //write
				{
					raf.seek(com[2].getValue(Long.class)*BLOCK_LENGTH);
					raf.write(this.getProcessor().getMemory(), com[3].getValue(Integer.class), BLOCK_LENGTH);
					break;
				}
				case 3: //read
					raf.seek(com[2].getValue(Long.class)*BLOCK_LENGTH);
					byte[] data = new byte[BLOCK_LENGTH];
					raf.readFully(data);
					System.arraycopy(data, 0, this.getProcessor().getMemory(), com[3].getValue(Integer.class), data.length);
					break;
				}
				
				ringIndex = (ringIndex + 1 < instance[2].getValue(Byte.class) ? ringIndex + 1 : 0);
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void initializeDevice()
	{
		instance = ctrlStruct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
		instance[0].setValue(Byte.class, (byte)diskFiles.length);
	}
}
