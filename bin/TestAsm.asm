#mode defines
;Directory Device
  DirectoryDevice
  {
     byte status
     byte devCount
     byte depStart
     byte depLength
     int depAddr
  }

  DeviceEntry
	{
		int deviceHash
		int deviceAddress
	 	byte occupationLength
	}

;ACPI Device
  ACPIDevice
  {
    byte status
    int cmdAddr
    long launchTime
    long currentTime
  }

  ACPICommand
  {
    byte mode
    int data
  }

;Custom strutures
  DynamicLinker
  {
    int memSize
    int entPtr
    int stackPtr
  }

#mode data
  int test = 17 + 18

  DeviceEntry[7] ent

  ACPICommand com

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
  64: MOV r2, #0x400dd3e
	do
		DeviceEntry[r3].deviceHash -> r1
		ADD r3, r3, #sizeof(DeviceEntry)
	while(r2 != r1)

	SUB r3, r3, #sizeof(DeviceEntry)

	;load tmpVar1 with the relevant address
	DeviceEntry[r3].deviceAddress -> r1

  com* -> r2
  ACPIDevice[r1].cmdAddr <- r2

  MOV r2, #0
  com.mode <- r2

  MOV r2, #1
  ACPIDevice[r1].status <- r2

  HLT
#data
