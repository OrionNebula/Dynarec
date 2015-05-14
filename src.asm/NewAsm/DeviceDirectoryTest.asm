#mode defines
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

	VideoDevice
	{
		byte status
		int width
		int height
		int instAddr
	}

	GraphicsCommand
	{
		byte mode
		int data
		byte x
		byte y
	}

	IntCtrl
	{
		byte status
		byte command
		int toMonitor
		byte regionLength
	}

	Keyboard
	{
		byte swap
		byte busy
		int bufferLen
		int readLen
		int addr
	}

	Keystroke
	{
		byte key
		byte char
	}

	MouseDevice
	{
		byte status
		int x
		int y
	}
#mode data
	int comPtr
	GraphicsCommand com

	int memSize

	int mod1
	int mod2
	int vidAddr

	int stackPtr
	int[16] returnStack

	DeviceEntry[16] ent
#mode text
	memSize <- r1

	;stack
	stackPtr* -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret3:
	stackPtr& <- r1
	64: MOV r1, #0x91304AC4
	mod1 <- r1
	goto :findDeviceMod1:
	:ret3:

	mod1 -> r1
	vidAddr <- r1

	if(r0 != r0)
		hlt
	end

	;call mod
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret:
	stackPtr& <- r1
	64: MOV r1, #300000
	mod1 <- r1
	64: MOV r1, #5245
	mod2 <- r1
	goto :mod:
	:ret:

	;call displayMod1
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret2:
	stackPtr& <- r1
	goto :displayMod1:
	:ret2:

	;call displayMod1
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret4:
	stackPtr& <- r1
	goto :displayBase10:
	:ret4:

	HLT
:displayBase10:
	vidAddr -> r1

	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2

	mod1 -> r2

	MOV r6, #63
	MOV r3, #0
	do
		DIV r4, r2, #10
		MUL r4, r4, #10
		SUB r4, r2, r4

		com.x <- r6
		SUB r6, r6, #1
		MOV r5, #1
		com.y <- r5

		ADD r4, r4, #48
		com.data <- r4

		MOV r4, #1
		comPtr <- r4
		VideoDevice[r1].status <- r4

		do
			VideoDevice[r1].status -> r4
		while(r3 != r4)

		DIV r2, r2, #10
	while(r2 != r3)

	goto :return:
:findDeviceMod1:
	memSize -> r1
	SUB r1, r1, #sizeof(DirectoryDevice)
	ent* -> r2
	DirectoryDevice[r1].depAddr <- r2

	DirectoryDevice[r1].devCount -> r2
	DirectoryDevice[r1].depLength <- r2
	MOV r2, #0
	DirectoryDevice[r1].depStart <- r2
	MOV r2, #1
	DirectoryDevice[r1].status <- r2

	MOV r3, #0
	do
		DirectoryDevice[r1].status -> r2
	while(r3 != r2)

	ent* -> r4
	mod1 -> r3
	do
		DeviceEntry[r4].deviceHash -> r2
		ADD r4, r4, #sizeof(DeviceEntry)
	while(r3 != r2)

	SUB r4, r4, #sizeof(DeviceEntry)

	DeviceEntry[r4].deviceAddress -> r2
	mod1 <- r2

	goto :return:
:displayMod1:
	vidAddr -> r1

	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2

	mod1 -> r2

	MOV r6, #63
	MOV r3, #0
	do
		AND r4, r2, #1

		com.x <- r6
		SUB r6, r6, #1
		MOV r5, #0
		com.y <- r5

		ADD r4, r4, #48
		com.data <- r4

		MOV r4, #1
		comPtr <- r4
		VideoDevice[r1].status <- r4

		do
			VideoDevice[r1].status -> r4
		while(r3 != r4)

		ASR r2, r2, #1
	while(r2 != r3)

	goto :return:
:mod:
	mod1 -> r1
	mod2 -> r2
	DIV r3, r1, r2
	MUL r3, r3, r2
	SUB r3, r1, r3
	mod1 <- r3
:return:
	stackPtr& -> r2
	stackPtr -> r1
	SUB r1, r1, #sizeof(int)
	stackPtr <- r1

	SUB r1, r2, r15
	SUB r1, r1, #12
	64: B r1, r0, r0, #0
#data
