#mode defines
  DynamicLinker
  {
    int memSize
    int entPtr
    int stackPtr
  }

  DeviceEntry
	{
		int deviceHash
		int deviceAddress
	 	byte occupationLength
	}

  DiskDevice
  {
    byte diskCt
    int ringAddr
    byte ringLength
  }

  DiskCommand
  {
    byte mode
    byte diskId
    long diskSector
    int memAddress
  }

#mode data
  DynamicLinker lnk
#mode text
  #data
#mode macro
  AbsJump(@addr)
  {
  	SUB r0, @addr, r15
  	SUB r0, r0, #12
  	64: B r0, r0, r0, #0
  }
#mode data
  DiskCommand[4] com
  byte[2048] init
#mode text
  lnk.entPtr -> r1

  64: MOV r2, #0x1d62abe0
  do
    DeviceEntry[r1].deviceHash -> r3
    ADD r1, r1, #sizeof(DeviceEntry)
  while(r2 != r3)
  SUB r1, r1, #sizeof(DeviceEntry)

  DeviceEntry[r1].deviceAddress -> r1

  MOV r2, #4
  DiskDevice[r1].ringLength <- r2

  MOV r2, #3
  com[0].mode <- r2
  com[1].mode <- r2
  com[2].mode <- r2
  com[3].mode <- r2
  MOV r2, #0
  com[0].diskId <- r2
  com[1].diskId <- r2
  com[2].diskId <- r2
  com[3].diskId <- r2
  MOV r2, #1
  com[0].diskSector <- r2
  ADD r2, r2, #1
  com[1].diskSector <- r2
  ADD r2, r2, #1
  com[2].diskSector <- r2
  ADD r2, r2, #1
  com[3].diskSector <- r2
  init* -> r2
  com[0].memAddress <- r2
  ADD r2, r2, #512
  com[1].memAddress <- r2
  ADD r2, r2, #512
  com[2].memAddress <- r2
  ADD r2, r2, #512
  com[3].memAddress <- r2

  com* -> r2
  DiskDevice[r1].ringAddr <- r2

  MOV r2, #0
  do
    com[3].mode -> r3
  while(r2 != r3)

  init* -> r2
  AbsJump(r2)
  #data
