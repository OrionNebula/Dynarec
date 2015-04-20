package net.lotrek.dynarec.devices;

import java.io.IOException;
import java.io.RandomAccessFile;


public class DiskDevice extends MemorySpaceDevice
{
	public static final int BLOCK_LENGTH = 512;
	/*
	 * 0 byte status
	 * 1 byte diskCt --total disk count
	 * 3 int ringAddr --instruction ring address
	 * 4 byte ringLength --length of ring in instructions
	 */
	private Structure ctrlStruct = new Structure(Byte.class, Byte.class, Integer.class, Byte.class),
			/*
			 * 0 byte mode --0 request metadata, 1 write, 2 read
			 * 1 byte diskId
			 * 2 long diskSector
			 * 3 int memAddress
			 */
			cmdStruct = new Structure(Byte.class, Byte.class, Long.class, Integer.class);
	private Register[] instance = ctrlStruct.getInstance(getOccupationAddr(), this.getProcessor().getMemory());
	private RandomAccessFile[] diskFiles;
	
	public DiskDevice(RandomAccessFile...diskFile)
	{
		this.diskFiles = diskFile;
		for (RandomAccessFile randomAccessFile : diskFile) {
			try {
				if(randomAccessFile.length() % BLOCK_LENGTH != 0)
					randomAccessFile.setLength(randomAccessFile.length() + (randomAccessFile.length() % BLOCK_LENGTH != 0 ? BLOCK_LENGTH - randomAccessFile.length() % BLOCK_LENGTH : 0));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public int getOccupationLength()
	{
		return ctrlStruct.getLength() + BLOCK_LENGTH;
	}

	//TODO: make not broken
	public void executeDeviceCycle()
	{
		try {
			instance[1].setValue(Byte.class, (byte)diskFiles.length);
			
			int status = (byte)instance[0].getValue(), diskRef = (byte)instance[2].getValue();
			if((status & 1) == 1) //update metadata
				instance[3].setValue(Long.class, diskFiles[diskRef].length() / BLOCK_LENGTH);
			if((status & 2) == 2) //io busy
			{
				switch(status & 4)
				{
				case 4: //write
					
					break;
				case 0: //read
					
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void initializeDevice()
	{
		instance[1].setValue(Byte.class, (byte)diskFiles.length);
	}
}
