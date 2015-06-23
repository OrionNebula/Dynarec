#mode defines
  DirectoryDevice
  {
     byte status
     byte devCount
     byte depStart
     byte depLength
     int depAddr
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

  DiskMetadata
  {
    int sectorSize
    int sectorCount
    int diskUID
  }

  IntCtrl
	{
		byte status
		byte command
		int toMonitor
		byte regionLength
	}

	KeyboardDevice
	{
		byte swap
		byte busy
		int bufferLen
		int readLen
		int addr
	}

  Keystroke
	{
		int key
		byte char
	}

  ;Custom Structures
  IVT
	{
		int monitorPtr
		int rtcPtr
	}

  DeviceAddresses
  {
    int vidAddr
    int intAddr
    int keyAddr
    int mouseAddr
    int rtcAddr
    int diskAddr
  }

  DeviceEntry
	{
		int deviceHash
		int deviceAddress
	 	byte occupationLength
	}
#mode macro
  CallMethod(@MethodPointer)
  {
    stackPtr -> r1
    ADD r1, r1, #sizeof(int)
    stackPtr <- r1
    ADD r1, r15, #24
    stackPtr& <- r1
    goto @MethodPointer
  }
#mode data
  DeviceAddresses addr
  int devAddr
  byte devLength
  int tmpVar1
	int tmpVar2
	int tmpVar3
	int memSize
	byte entLoaded
	DeviceEntry[7] ent

  IVT ivtTable

  Keystroke key

  DiskCommand[10] commandRing

  ;Method call return pointer stack
  int stackPtr
  int[16] returnStack

  int dataPtr
  byte[512] diskData
#mode text
  memSize <- r1

  ;Prepare IVT
  ivtTable* -> r12
  64: ADD r1, r15, #:keyInterrupt:
  ivtTable.monitorPtr <- r1

  ;Prepare the parameters for device load
  64: MOV r1, #-1859106108
  addr.vidAddr <- r1
  64: MOV r1, #-2043936910
  addr.intAddr <- r1
  64: MOV r1, #1377772586
  addr.keyAddr <- r1
  64: MOV r1, #-2068042898
  addr.mouseAddr <- r1
  64: MOV r1, #-1017264500
  addr.rtcAddr <- r1
  64: MOV r1, #0x1d62abe0
  addr.diskAddr <- r1
  MOV r1, #sizeof(DeviceAddresses)
  DIV r1, r1, #sizeof(int)
  devLength <- r1
  addr* -> r1
  devAddr <- r1

  ;Prepare the return stack
  stackPtr* -> r1
  stackPtr <- r1

  ;Load the device addresses
  CallMethod(:loadDeviceList:)

  addr.diskAddr -> r1
  commandRing* -> r2
  DiskDevice[r1].ringAddr <- r2
  MOV r2, #10
  DiskDevice[r1].ringLength <- r2

  diskData* -> r1
  dataPtr <- r1

  ;Prepare keyboard interrupt
  addr.intAddr -> r1
	addr.keyAddr -> r2
	IntCtrl[r1].toMonitor <- r2
	MOV r2, #0
	IntCtrl[r1].command <- r2
	MOV r2, #1
	IntCtrl[r1].regionLength <- r2
	IntCtrl[r1].status <- r2

	MOV r3, #0
	do
		IntCtrl[r1].status -> r2
	while(r3 != r2)

  HLT
:keyInterrupt:
  addr.keyAddr -> r1
  key* -> r2
  KeyboardDevice[r1].addr <- r2
	MOV r2, #1
	KeyboardDevice[r1].readLen <- r2
	KeyboardDevice[r1].busy <- r2

  MOV r3, #0
	do
		KeyboardDevice[r1].busy -> r2
	while(r2 != r3)

	key.key -> r2

  MOV r1, #28
  if(r1 = r2)
    commandRing* -> r1
    MOV r2, #0
    DiskCommand[r1].diskId <- r2
    DiskCommand[r1].diskSector <- r2
    diskData* -> r2
    DiskCommand[r1].memAddress <- r2
    MOV r2, #2
    DiskCommand[r1].mode <- r2

    RET
  end

  key.char -> r2

  dataPtr -> r1
  L@byte[r1].value <- r2
  ADD r1, r1, #1
  dataPtr <- r1

  RET
:loadDeviceList:
	MOV r4, #0
	do
		devAddr -> r1
		MUL r2, r4, #sizeof(int)
		ADD r1, r2, r1
		L@int[r1].value -> r1
		tmpVar1 <- r1

		CallMethod(:findDevice:)

		devAddr -> r1
		MUL r2, r4, #sizeof(int)
		ADD r1, r2, r1
		tmpVar1 -> r2
		L@int[r1].value <- r2

		devLength -> r2
		ADD r4, r4, #1
	while(r4 != r2)

	goto :return:
:findDevice:
	MOV r2, #0
	entLoaded -> r1
	if(r2 = r1)
		;Obtain the address for the DirectoryDevice -> latched to memory end
		memSize -> r1
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
	end

	MOV r1, #1
	entLoaded <- r1

	;loop over the loaded devices until a hash matches
	ent* -> r3
	tmpVar1 -> r2
	do
		DeviceEntry[r3].deviceHash -> r1
		ADD r3, r3, #sizeof(DeviceEntry)
	while(r2 != r1)

	SUB r3, r3, #sizeof(DeviceEntry)

	;load tmpVar1 with the relevant address
	DeviceEntry[r3].deviceAddress -> r1
	tmpVar1 <- r1
:return:
	;load the latest stack value, decrement the stack pointer and re-store
	stackPtr& -> r2
	stackPtr -> r1
	SUB r1, r1, #sizeof(int)
	stackPtr <- r1

	;obtain a relative jump vector and jump to it
	SUB r1, r2, r15
	SUB r1, r1, #12
	64: B r1, r0, r0, #0

#data
