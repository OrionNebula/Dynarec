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
	;Graphics interface vars
	int comPtr
	GraphicsCommand com

	;stores total memory size
	int memSize

	;mod1, used as a standard transfer var
	int mod1
	;mod2, used as a render temp storage
	int mod2
	;stores the video device address
	int vidAddr

	;Method call return pointer stack
	int stackPtr
	int[16] returnStack

	;Arbitrary space to store the device descriptor list
	DeviceEntry[16] ent
#mode text
	memSize <- r1

	;setup stack pointer
	stackPtr* -> r1
	stackPtr <- r1

	;call findDeviceMod1, locate device with hash -1859106108 (VideoDevice) and store the address in mod1
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret3:
	stackPtr& <- r1
	64: MOV r1, #-1859106108
	mod1 <- r1
	goto :findDeviceMod1:
	:ret3:

	;store VideoDevice address in vidAddr
	mod1 -> r1
	vidAddr <- r1

	;Arbitrary number to render
	64: MOV r1, #654646
	mod1 <- r1

	;call displayBase10 -> displays mod1 in the top left corner in base 10
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

	;save mod1 through the method call
	mod1 -> r2
	mod2 <- r2

	;call logbase10 -> obtain the number of digits - 1 in mod1
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret:
	stackPtr& <- r1
	goto :logbase10:
	:ret:

	;Recall mod1 as the var to render, move digit count into the initial x register
	vidAddr -> r1
	mod2 -> r2
	mod1 -> r6
	MOV r3, #0
	do
		;take the modulus of r2 (render reg)
		DIV r4, r2, #10
		MUL r4, r4, #10
		SUB r4, r2, r4

		;set the command params
		com.x <- r6
		SUB r6, r6, #1
		MOV r5, #0
		com.y <- r5

		;locate and store the ascii value for the relevant digit
		ADD r4, r4, #48
		com.data <- r4

		;signal the screen to render our command
		MOV r4, #1
		comPtr <- r4
		VideoDevice[r1].status <- r4

		;wait for the screen to process
		do
			VideoDevice[r1].status -> r4
		while(r3 != r4)

		;remove the rightmost digit from r2
		DIV r2, r2, #10
	while(r2 != r3)

	goto :return:
:logbase10:
	;use the change of base formula to calculate the log10 of mod1
	mod1 -> r1
	DFI r1
	64: DLN r1, r1
	MOV r2, #10
	DFI r2
	64: DLN r2, r2
	64: DDIV r1, r1, r2
	IFD r1
	mod1 <- r1

	goto :return:
:findDeviceMod1:
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

	;loop over the loaded devices until a hash matches
	ent* -> r4
	mod1 -> r3
	do
		DeviceEntry[r4].deviceHash -> r2
		ADD r4, r4, #sizeof(DeviceEntry)
	while(r3 != r2)

	SUB r4, r4, #sizeof(DeviceEntry)

	;load mod1 with the relevant address
	DeviceEntry[r4].deviceAddress -> r2
	mod1 <- r2
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
