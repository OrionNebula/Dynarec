#mode imports
  src.asm/stdlib.asmh
  src.asm/OS/bios.asmh
#mode data
  DeviceEntry[7] ent

  DiskCommand com

  ;method stack
  int[16] stack

  int memSize

  DynamicLinker data

  byte[500] toLoad ;subtract sizeof(DynamicLinker) before assembling
#mode text
  memSize <- r1

	;Obtain the address for the DirectoryDevice -> latched to memory end
	SUB r1, r1, #sizeof(DirectoryDevice)
	;prepare to load the directory descriptors into the array at ent*
	ent* -> r2
	DirectoryDevice[r1].depAddr <- r2

	MOV r3, #0
	do
		DirectoryDevice[r1].devCount -> r2
	while(r2 = r3)

	;load every device descriptor into ent*
	DirectoryDevice[r1].depLength <- r2
	MOV r2, #0
	DirectoryDevice[r1].depStart <- r2
	MOV r2, #1
	DirectoryDevice[r1].status <- r2

	;wait for the device to complete its action
	do
		DirectoryDevice[r1].status -> r2
	while(r3 != r2)

	;loop over the loaded devices until a hash matches
	ent* -> r3
  64: MOV r2, #0x1d62abe0
	do
		DeviceEntry[r3].deviceHash -> r1
		ADD r3, r3, #sizeof(DeviceEntry)
	while(r2 != r1)

	SUB r3, r3, #sizeof(DeviceEntry)

	;load tmpVar1 with the relevant address
	DeviceEntry[r3].deviceAddress -> r1

  com* -> r2
  DiskDevice[r1].ringAddr <- r2
  MOV r2, #3
  com.mode <- r2
  MOV r2, #0
  com.diskId <- r2
  com.diskSector <- r2
  data* -> r2
  com.memAddress <- r2

  MOV r2, #1
  DiskDevice[r1].ringLength <- r2

  MOV r1, #0
  do
    com[3].mode -> r2
  while(r1 != r2)

  memSize -> r2
  data.memSize <- r2
  ent* -> r2
  data.entPtr <- r2
  stack* -> r2
  data.stackPtr <- r2

  toLoad* -> r2

	;obtain a relative jump vector and jump to it
  AbsJump(r2)
#data
