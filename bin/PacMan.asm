#mode defines
	DirectoryDevice
	{
		byte status
	 	byte devCount
	 	byte depStart
	 	byte depLength
	 	int depAddr
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

	KeyboardDevice
	{
		byte swap
		byte busy
		int bufferLen
		int readLen
		int addr
	}

	MouseDevice
	{
		int x
		int y
	}

	RTCDevice
	{
		byte id
		byte command
		int period
	}

	;Custom Structures
	IVT
	{
		int monitorPtr
		int rtcPtr
	}

	GameEntity
	{
		byte x
		byte y
		byte char
		int color
		byte m1
		byte m2
		byte m3
		byte m4
	}

	DeviceEntry
	{
		int deviceHash
		int deviceAddress
	 	byte occupationLength
	}

	DeviceAddresses
	{
		int vidAddr
		int intAddr
		int keyAddr
		int mouseAddr
		int rtcAddr
	}

	Keystroke
	{
		int key
		byte char
	}

	GraphicsQuery
	{
		byte char
		int color
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
	DeviceEntry[6] ent

	;Graphics
	int comPtr
	GraphicsCommand char
	GraphicsCommand color
	GraphicsQuery query

	;Method call return pointer stack
	int stackPtr
	int[16] returnStack

	;tmpValue stack
	int tmpStackPtr
	int[16] tmpStack

	;Ghost AI state information
	int minDistance
	byte aiHeading

	IVT ivtTable

	;Game vars
	GameEntity pacMan
	GameEntity blinky ;red ghost
	GameEntity pinky  ;pink ghost
	GameEntity inky   ;blue ghost
	GameEntity clyde  ;orange ghost
	GameEntity dot	  ;tmp var for dot render
	GameEntity wall	  ;tmp var for dot render
	int numDots

	Keystroke key

#mode macro
	POP(@StackPtr, @ToPop)
	{
		@StackPtr& -> @ToPop
		@StackPtr -> r1
		SUB r1, r1, #sizeof(int)
		@StackPtr <- r1
	}

	PUSH(@StackPtr, @ToPush)
	{
		MOV r2, @ToPush
		@StackPtr -> r1
		ADD r1, r1, #sizeof(int)
		@StackPtr <- r1
		@StackPtr& <- r2
	}

	CallMethod(@MethodPointer)
	{
		stackPtr -> r1
		ADD r1, r1, #sizeof(int)
		stackPtr <- r1
		ADD r1, r15, #24
		stackPtr& <- r1
		goto @MethodPointer
	}

	RenderEntity(@EntityVar)
	{
		@EntityVar* -> r1
		tmpVar1 <- r1
		CallMethod(:renderEntity:)
	}

	WaitForVideoDeviceStatus(@AddrReg, @ValueReg, @CmpReg)
	{
		VideoDevice[@AddrReg].status <- @ValueReg
		do
			VideoDevice[@AddrReg].status -> @ValueReg
		while(@CmpReg != @ValueReg)
	}

	JumpAbsolute(@JumpAddr)
	{
		SUB r0, @JumpAddr, r15
		SUB r0, r0, #12
		64: B r0, r0, r0, #0
	}

	HeadingSwap(@ToSwap, @TmpReg)
	{
		AND @TmpReg, @ToSwap, #3
		LSR @ToSwap, @ToSwap, #2
		LSL @TmpReg, @TmpReg, #2
		ORR @ToSwap, @ToSwap, r5
	}
#mode text
	memSize <- r1

	ivtTable* -> r12
	64: ADD r1, r15, #:keyboardInterrupt:
	ivtTable.monitorPtr <- r1
	64: ADD r1, r15, #:gameLoop:
	ivtTable.rtcPtr <- r1

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
	MOV r1, #sizeof(DeviceAddresses)
	DIV r1, r1, #sizeof(int)
	devLength <- r1
	addr* -> r1
	devAddr <- r1

	;Prepare the return stack
	stackPtr* -> r1
	stackPtr <- r1
	tmpStackPtr* -> r1
	tmpStackPtr <- r1

	;Load the device addresses
	CallMethod(:loadDeviceList:)

	CallMethod(:setupKeyboardInterrupt:)

	;Set up pacMan
	MOV r1, #5
	pacMan.x <- r1
	pacMan.y <- r1
	64: MOV r1, #0xffff00
	pacMan.color <- r1
	MOV r1, #2
	pacMan.char <- r1

	;Set up dots
	64: MOV r1, #0xffc2a4
	dot.color <- r1
	MOV r1, #7 ;�
	dot.char <- r1

	;Set up walls
	64: MOV r1, #0x2121de
	wall.color <- r1
	MOV r1, #8
	wall.char <- r1

	;setup pacMan target
	MOV r1, #1
	pacMan.m1 <- r1
	MOV r1, #33
	pacMan.m2 <- r1
	MOV r1, #20
	pacMan.m3 <- r1

	;set up blinky
	MOV r1, #10
	blinky.x <- r1
	blinky.y <- r1
	64: MOV r1, #0xff0000
	blinky.color <- r1
	MOV r1, #1
	blinky.char <- r1

	CallMethod(:fillWithWalls:)
	CallMethod(:fillWithDots:)

	addr.rtcAddr -> r1

	MOV r2, #50;166
	RTCDevice[r1].period <- r2
	MOV r2, #1
	RTCDevice[r1].id <- r2

	HLT
:gameLoop:
	numDots -> r1
	MOV r2, #0
	if(r1 > r2)
		pacMan* -> r1
		tmpVar1 <- r1
		CallMethod(:clearEntity:)
		pacMan* -> r1
		tmpVar1 <- r1
		CallMethod(:moveEntityWithHeading:)
		RenderEntity(pacMan)

		pacMan.x -> r1
		blinky.m2 <- r1
		pacMan.y -> r1
		blinky.m3 <- r1

		blinky* -> r1
		tmpVar1 <- r1
		CallMethod(:clearEntity:)
		blinky* -> r1
		tmpVar1 <- r1
		CallMethod(:directGhost:)

		blinky.m4 -> r1
		MOV r2, #1
		if(r1 = r2)
			MOV r2, #0
			blinky.m4 <- r2
			RenderEntity(dot)
		end

		blinky* -> r1
		tmpVar1 <- r1
		CallMethod(:moveEntityWithHeading:)
		RenderEntity(blinky)
	end

	RET
:setupKeyboardInterrupt:
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

	goto :return:
:keyboardInterrupt:
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

	key.key -> r1

	MOV r2, #200
	if(r2 = r1)
		MOV r1, #4
	end
	MOV r2, #208
	if(r2 = r1)
		MOV r1, #1
	end
	MOV r2, #203
	if(r2 = r1)
		MOV r1, #8
	end
	MOV r2, #205
	if(r2 = r1)
		MOV r1, #2
	end

	pacMan.m1 <- r1

	RET
:moveEntityWithHeading:
	tmpVar1 -> r1
	tmpVar3 <- r1
	GameEntity[r1].x -> r2
	GameEntity[r1].y -> r3
	GameEntity[r1].m1 -> r4

	;Rightmost bit is down
	AND r5, r4, #1
	ADD r3, r3, r5
	;2nd right bit is right
	LSR r4, r4, #1
	AND r5, r4, #1
	ADD r2, r2, r5
	;3rd right bit is up
	LSR r4, r4, #1
	AND r5, r4, #1
	SUB r3, r3, r5
	;4th right bit is left
	LSR r4, r4, #1
	AND r5, r4, #1
	SUB r2, r2, r5

	tmpVar1 <- r2
	tmpVar2 <- r3

	CallMethod(:testPixel:)

	tmpVar3 -> r1

	tmpVar1 -> r2
	tmpVar2 -> r3

	query.char -> r5
	MOV r4, #7
	if(r5 = r4)
		pacMan* -> r4
		if(r1 != r4)
			dot.x <- r2
			dot.y <- r3
		end
		if(r1 = r4)
			numDots -> r4
			SUB r4, r4, #1
			numDots <- r4
		end
		GameEntity[r1].x <- r2
		GameEntity[r1].y <- r3
		MOV r2, #1
		GameEntity[r1].m4 <- r2
	end
	MOV r4, #0
	if(r5 = r4)
		GameEntity[r1].x <- r2
		GameEntity[r1].y <- r3
	end

	goto :return:
:directGhost:
	tmpVar1 -> r1
	PUSH(tmpStackPtr, r1)

	64: MOV r1, #0
	minDistance <- r1
	MOV r1, #0
	aiHeading <- r1

	MOV r2, #1
	PUSH(tmpStackPtr, r2)
	do
		tmpStackPtr -> r1
		SUB r1, r1, #sizeof(int)
		L@int[r1].value -> r1
		tmpVar1 <- r1
		tmpStackPtr& -> r1
		tmpVar3 <- r1
		CallMethod(:testHeading:)

		query.char -> r1
		MOV r2, #0
		if(r1 = r2) goto :or2:
		MOV r2, #7
		if(r1 = r2)
			:or2:

			tmpStackPtr -> r1
			SUB r1, r1, #sizeof(int)
			L@int[r1].value -> r1

			GameEntity[r1].m2 -> r2
			GameEntity[r1].m3 -> r3

			GameEntity[r1].m1 -> r4
			tmpStackPtr& -> r1
			HeadingSwap(r1, r5)
			if(r4 != r1)
				char.x -> r1
				SUB r2, r2, r1

				char.y -> r1
				SUB r3, r3, r1

				MUL r2, r2, r2
				MUL r3, r3, r3

				ADD r1, r2, r3

				minDistance -> r2
				MOV r3, #0
				if(r2 = r3) goto :or1:
				if(r1 < r2)
					:or1:
					minDistance <- r1
					tmpStackPtr& -> r1
					aiHeading <- r1
				end
			end
		end

		tmpStackPtr& -> r1
		MUL r1, r1, #2
		tmpStackPtr& <- r1
		MOV r2, #16
	while(r1 < r2)

	POP(tmpStackPtr, r1)
	tmpStackPtr& -> r1

	aiHeading -> r2
	GameEntity[r1].m1 <- r2

	POP(tmpStackPtr, r1)

	goto :return:
:testHeading:
	tmpVar1 -> r1
	GameEntity[r1].x -> r2
	GameEntity[r1].y -> r3

	tmpVar3 -> r4
	;Rightmost bit is down
	AND r5, r4, #1
	ADD r3, r3, r5
	;2nd right bit is right
	LSR r4, r4, #1
	AND r5, r4, #1
	ADD r2, r2, r5
	;3rd right bit is up
	LSR r4, r4, #1
	AND r5, r4, #1
	SUB r3, r3, r5
	;4th right bit is left
	LSR r4, r4, #1
	AND r5, r4, #1
	SUB r2, r2, r5

	tmpVar1 <- r2
	tmpVar2 <- r3

	CallMethod(:testPixel:)

	goto :return:
:fillWithWalls:
	MOV r1, #62
	tmpVar1 <- r1
	MOV r1, #32
	tmpVar2 <- r1

	do
		tmpVar1 -> r1
		SUB r1, r1, #1
		tmpVar1 <- r1
		do
			tmpVar2 -> r1
			SUB r1, r1, #2
			tmpVar2 <- r1


			tmpVar1 -> r1
			tmpVar3 <- r1
			wall.x <- r1
			tmpVar2 -> r1
			wall.y <- r1

			;Render dot
			RenderEntity(wall)

			tmpVar3 -> r1
			tmpVar1 <- r1

			MOV r2, #0
			tmpVar2 -> r1
		while(r2 != r1)

		MOV r1, #32
		tmpVar2 <- r1

		MOV r2, #2
		tmpVar1 -> r1
	while(r2 != r1)

	goto :return:

:fillWithDots:
	MOV r1, #64
	tmpVar1 <- r1
	MOV r1, #32
	tmpVar2 <- r1

	do
		tmpVar1 -> r1
		SUB r1, r1, #1
		tmpVar1 <- r1
		do
			tmpVar2 -> r1
			SUB r1, r1, #1
			tmpVar2 <- r1

			;Load pixel test
			CallMethod(:testPixel:)

			MOV r1, #0
			query.char -> r2
			if(r1 = r2)
				tmpVar1 -> r1
				tmpVar3 <- r1
				dot.x <- r1
				tmpVar2 -> r1
				dot.y <- r1

				;Render dot
				RenderEntity(dot)

				numDots -> r1
				ADD r1, r1, #1
				numDots <- r1

				tmpVar3 -> r1
				tmpVar1 <- r1
			end

			MOV r2, #0
			tmpVar2 -> r1
		while(r2 != r1)

		MOV r1, #32
		tmpVar2 <- r1

		MOV r2, #0
		tmpVar1 -> r1
	while(r2 != r1)

	goto :return:
:testPixel:
	;Prepare the video device
	addr.vidAddr -> r1
	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2

	;Prepare the command set
	MOV r1, #1
	comPtr <- r1

	;Prepare the testfor command
	MOV r1, #2
	char.mode <- r1
	tmpVar1 -> r1
	char.x <- r1
	tmpVar2 -> r1
	char.y <- r1
	query* -> r1
	char.data <- r1

	addr.vidAddr -> r1
	MOV r2, #1
	MOV r3, #0
	WaitForVideoDeviceStatus(r1, r2, r3)

	goto :return:
:clearEntity:
	;Prepare the video device
	addr.vidAddr -> r1
	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2

	;Prepare the command set
	MOV r1, #2
	comPtr <- r1

	tmpVar1 -> r1
	GameEntity[r1].x -> r2
	char.x <- r2
	color.x <- r2
	GameEntity[r1].y -> r2
	char.y <- r2
	color.y <- r2
	MOV r2, #0
	char.data <- r2
	GameEntity[r1].color -> r2
	color.data <- r2

	MOV r1, #0
	char.mode <- r1
	MOV r1, #1
	color.mode <- r1

	addr.vidAddr -> r2
	MOV r3, #0
	WaitForVideoDeviceStatus(r2, r1, r3)

	goto :return:
:renderEntity:
	;Prepare the video device
	addr.vidAddr -> r1
	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2

	;Prepare the command set
	MOV r1, #2
	comPtr <- r1

	tmpVar1 -> r1
	GameEntity[r1].x -> r2
	char.x <- r2
	color.x <- r2
	GameEntity[r1].y -> r2
	char.y <- r2
	color.y <- r2
	GameEntity[r1].char -> r2
	char.data <- r2
	GameEntity[r1].color -> r2
	color.data <- r2

	MOV r1, #0
	char.mode <- r1
	MOV r1, #1
	color.mode <- r1

	addr.vidAddr -> r2
	MOV r3, #0
	WaitForVideoDeviceStatus(r2, r1, r3)

	goto :return:
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
	JumpAbsolute(r2)

	#data
