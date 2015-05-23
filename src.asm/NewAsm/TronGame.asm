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

	LightCycle
	{
		byte hasCrashed
		byte x
		byte y
		byte heading
		int color
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
	;cach device addresses
	DeviceAddresses addr

	;Graphics interface vars
	int comPtr
	GraphicsCommand com
	GraphicsCommand color
	GraphicsQuery testFor

	Keystroke key

	LightCycle player
	byte numCycles
	LightCycle[5] enemies

	IVT intTbl

	byte testX
	byte testY

	long randState
	int genRand

	;stores total memory size
	int memSize

	;mod1, used as a standard transfer var
	int mod1
	;mod2, used as a render temp storage
	int mod2

	byte iter
	byte totCrashed

	;Method call return pointer stack
	int stackPtr
	int[16] returnStack

	byte entLoaded
	;Arbitrary space to store the device descriptor list
	DeviceEntry[16] ent
#mode text
	memSize <- r1

	intTbl* -> r12
	64: ADD r1, r15, #:updatePlayerCycle:
	intTbl.monitorPtr <- r1
	64: ADD r1, r15, #:gameTick:
	intTbl.rtcPtr <- r1

	;setup stack pointer
	stackPtr* -> r1
	stackPtr <- r1

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #24
	stackPtr& <- r1
	goto :loadDeviceDescriptors:

	addr.mouseAddr -> r1
	MOV r4, #0
	do
		MouseDevice[r1].x -> r2
		MouseDevice[r1].y -> r3
		XOR r2, r2, r3
	while(r4 = r2)
	randState <- r2

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #24
	stackPtr& <- r1
	goto :setupKeyboardInterrupt:

	MOV r1, #5
	player.x <- r1
	MOV r1, #16
	player.y <- r1
	MOV r1, #2
	player.heading <- r1
	64: MOV r1, #0x0051FF
	player.color <- r1

	MOV r1, #4
	MOV r5, #0
	numCycles <- r1
	do
		MUL r1, r5, #sizeof(LightCycle)
		enemies* -> r2
		ADD r1, r2, r1
		mod1 <- r1

		stackPtr -> r1
		ADD r1, r1, #sizeof(int)
		stackPtr <- r1
		ADD r1, r15, #24
		stackPtr& <- r1
		goto :initEnemyCycle:

		ADD r5, r5, #1
		numCycles -> r1
	while(r5 != r1)

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:retsp:
	stackPtr& <- r1
	player* -> r1
	mod1 <- r1
	goto :initPlayerCycle:
	:retsp:

	addr.rtcAddr -> r1

	MOV r2, #75
	RTCDevice[r1].period <- r2
	MOV r2, #1
	RTCDevice[r1].id <- r2

	HLT
:gameTick:
	MOV r5, #0
	iter <- r5
	do
		iter -> r5
		MUL r1, r5, #sizeof(LightCycle)
		enemies* -> r2
		ADD r1, r2, r1
		mod1 <- r1

		MOV r2, #1
		LightCycle[r1].hasCrashed -> r1
		if(r1 != r2)

			stackPtr -> r1
			ADD r1, r1, #sizeof(int)
			stackPtr <- r1
			ADD r1, r15, #24
			stackPtr& <- r1
			goto :runAITick:

			stackPtr -> r1
			ADD r1, r1, #sizeof(int)
			stackPtr <- r1
			ADD r1, r15, #24
			stackPtr& <- r1
			goto :moveLightCycle:

		end

		iter -> r5
		ADD r5, r5, #1
		iter <- r5
		numCycles -> r1
	while(r5 != r1)

	player.hasCrashed -> r1
	MOV r2, #1
	if(r2 != r1)
		stackPtr -> r1
		ADD r1, r1, #sizeof(int)
		stackPtr <- r1
		ADD r1, r15, #:retp:
		stackPtr& <- r1
		player* -> r1
		mod1 <- r1
		goto :moveLightCycle:
		:retp:
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
:initPlayerCycle:
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #24
	stackPtr& <- r1
	goto :generateRandom:

	genRand -> r1

	mod1 -> r3

	AND r2, r1, #63
	LightCycle[r3].x <- r2
	AND r2, r1, #31
	LightCycle[r3].y <- r2
	AND r2, r1, #3
	LightCycle[r3].heading <- r2

	goto :return:
:initEnemyCycle:
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #24
	stackPtr& <- r1
	goto :generateRandom:

	mod1 -> r3

	genRand -> r1
	LightCycle[r3].color <- r1

	AND r2, r1, #63
	LightCycle[r3].x <- r2
	AND r2, r1, #31
	LightCycle[r3].y <- r2
	AND r2, r1, #3
	LightCycle[r3].heading <- r2

	goto :return:
:generateRandom:
	randState -> r1

	LSR r2, r1, #12
	XOR r1, r2, r1

	LSL r2, r1, #25
	XOR r1, r2, r1

	LSL r2, r1, #27
	XOR r1, r2, r1

	randState <- r1

	64: MOV r2, #0x2545F491
	LSL r2, r2, #32
	64: MOV r1, #0x4F6CDD1D
	ORR r2, r1, r2

	randState -> r1

	MUL r1, r1, r2
	64: MOV r2, #0xFFFFFF
	AND r1, r1, r2

	LSR r2, r1, #12
	XOR r1, r2, r1

	LSL r2, r1, #25
	XOR r1, r2, r1

	LSL r2, r1, #27
	XOR r1, r2, r1

	randState <- r1

	64: MOV r2, #0x2545F491
	LSL r2, r2, #32
	64: MOV r1, #0x4F6CDD1D
	ORR r2, r1, r2

	randState -> r1

	MUL r1, r1, r2
	64: MOV r2, #0xFFFFFF
	AND r1, r1, r2
	genRand <- r1

	goto :return:
:endGame:
	mod1 -> r1

	player* -> r2
	if(r2 = r1)
		addr.intAddr -> r1
		IntCtrl[r1].toMonitor <- r2
		MOV r2, #1
		LSL r2, r2, #7
		IntCtrl[r1].command <- r2
		MOV r2, #1
		IntCtrl[r1].status <- r2

		MOV r3, #0
		do
			IntCtrl[r1].status -> r2
		while(r3 != r2)
	end

	mod1 -> r1

	MOV r2, #1
	LightCycle[r1].hasCrashed <- r2
	testX -> r2
	com.x <- r2
	color.x <- r2
	testY -> r2
	com.y <- r2
	color.y <- r2
	MOV r2, #0
	com.mode <- r2
	MOV r2, #1
	color.mode <- r2
	MOV r2, #'X'
	com.data <- r2
	64: MOV r2, #0xFF0000
	color.data <- r2
	MOV r2, #2
	comPtr <- r2

	MOV r2, #1
	addr.vidAddr -> r1
	VideoDevice[r1].status <- r2

	MOV r3, #0
	do
		VideoDevice[r1].status -> r2
	while(r3 != r2)

	totCrashed -> r1
	ADD r1, r1, #1
	totCrashed <- r1
	numCycles -> r2
	if(r2 = r1)
		player.hasCrashed -> r1
		MOV r2, #1
		if(r1 != r2)
			addr.intAddr -> r1
			IntCtrl[r1].toMonitor <- r2
			MOV r2, #1
			LSL r2, r2, #7
			IntCtrl[r1].command <- r2
			MOV r2, #1
			IntCtrl[r1].status <- r2

			MOV r3, #0
			do
				IntCtrl[r1].status -> r2
			while(r3 != r2)
		end

		addr.rtcAddr -> r1

		MOV r2, #1
		RTCDevice[r1].command <- r2
		RTCDevice[r1].id <- r2

		HLT
	end

	goto :return:
:runAITick:
	mod1 -> r1
	LightCycle[r1].x -> r2
	LightCycle[r1].y -> r3
	LightCycle[r1].heading -> r1

	MOV r4, #0
	if(r4 = r1)
		SUB r3, r3, #1
	end
	MOV r4, #1
	if(r4 = r1)
		ADD r3, r3, #1
	end
	MOV r4, #3
	if(r4 = r1)
		SUB r2, r2, #1
	end
	MOV r4, #2
	if(r4 = r1)
		ADD r2, r2, #1
	end

	testX <- r2
	testY <- r3

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:reta:
	stackPtr& <- r1
	goto :testForCrash:
	:reta:

	mod2 -> r1
	MOV r2, #1
	if(r1 = r2)
		MOV r5, #0
		MOV r6, #4
		do
			mod1 -> r1
			LightCycle[r1].x -> r2
			LightCycle[r1].y -> r3
			MOV r1, r5

			MOV r4, #0
			if(r4 = r1)
				SUB r3, r3, #1
			end
			MOV r4, #1
			if(r4 = r1)
				ADD r3, r3, #1
			end
			MOV r4, #3
			if(r4 = r1)
				SUB r2, r2, #1
			end
			MOV r4, #2
			if(r4 = r1)
				ADD r2, r2, #1
			end

			testX <- r2
			testY <- r3

			stackPtr -> r1
			ADD r1, r1, #sizeof(int)
			stackPtr <- r1
			ADD r1, r15, #:reta2:
			stackPtr& <- r1
			goto :testForCrash:
			:reta2:

			MOV r1, #0
			mod2 -> r2
			if(r1 = r2)
				mod1 -> r1
				LightCycle[r1].heading <- r5
			end

			ADD r5, r5, #1
		while(r5 < r6)
	end

	goto :return:
:testForCrash:
	testX -> r2
	testY -> r3

	com.x <- r2
	com.y <- r3

	MOV r4, #0
	if(r2 < r4)
		MOV r2, #0
		testX <- r2
		goto :endTest:
	end
	if(r3 < r4)
		MOV r3, #0
		testY <- r3
		goto :endTest:
	end
	MOV r4, #64
	if(r2 >= r4)
		MOV r2, #63
		testX <- r2
		goto :endTest:
	end
	MOV r4, #32
	if(r3 >= r4)
		MOV r3, #31
		testY <- r3
		goto :endTest:
	end

	testFor* -> r2
	com.data <- r2

	addr.vidAddr -> r1
	MOV r2, #2
	com.mode <- r2
	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2
	MOV r2, #1
	comPtr <- r2
	VideoDevice[r1].status <- r2

	MOV r3, #0
	do
		VideoDevice[r1].status -> r2
	while(r3 != r2)

	MOV r1, #0
	testFor.char -> r2
	if(r3 != r2)
		:endTest:
		MOV r1, #1
	end

	mod2 <- r1

	goto :return:
:moveLightCycle:
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:rett:
	stackPtr& <- r1
	goto :renderLightTrail:
	:rett:

	mod1 -> r1
	LightCycle[r1].x -> r2
	LightCycle[r1].y -> r3
	LightCycle[r1].heading -> r1

	MOV r4, #0
	if(r4 = r1)
		SUB r3, r3, #1
	end
	MOV r4, #1
	if(r4 = r1)
		ADD r3, r3, #1
	end
	MOV r4, #3
	if(r4 = r1)
		SUB r2, r2, #1
	end
	MOV r4, #2
	if(r4 = r1)
		ADD r2, r2, #1
	end

	mod1 -> r1
	LightCycle[r1].x <- r2
	testX <- r2
	LightCycle[r1].y <- r3
	testY <- r3

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:retc:
	stackPtr& <- r1
	goto :testForCrash:
	:retc:

	MOV r2, #1
	mod2 -> r1
	if(r1 = r2) goto :endGame:

	goto :renderLightCycle:
:updatePlayerCycle:
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
		MOV r1, #0
	end
	MOV r2, #208
	if(r2 = r1)
		MOV r1, #1
	end
	MOV r2, #203
	if(r2 = r1)
		MOV r1, #3
	end
	MOV r2, #205
	if(r2 = r1)
		MOV r1, #2
	end

	player.heading <- r1

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret11:
	stackPtr& <- r1
	player* -> r1
	mod1 <- r1
	goto :renderLightCycle:
	:ret11:

	RET
:renderLightTrail:
	mod1 -> r1

	LightCycle[r1].x -> r2
	com.x <- r2
	color.x <- r2
	LightCycle[r1].y -> r2
	com.y <- r2
	color.y <- r2
	LightCycle[r1].color -> r2
	color.data <- r2
	LightCycle[r1].heading -> r2

	MOV r3, #1
	if(r2 <= r3)
		64: MOV r2, #124
		com.data <- r2
	end

	LightCycle[r1].heading -> r2
	if(r2 > r3)
		64: MOV r2, #45
		com.data <- r2
	end

	MOV r2, #2
	comPtr <- r2
	MOV r2, #1
	color.mode <- r2
	MOV r2, #0
	com.mode <- r2

	addr.vidAddr -> r1
	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2
	MOV r2, #1
	VideoDevice[r1].status <- r2
	MOV r2, #0
	do
		VideoDevice[r1].status -> r3
	while(r2 != r3)

	goto :return:
:renderLightCycle:
	mod1 -> r1

	LightCycle[r1].x -> r2
	com.x <- r2
	color.x <- r2
	LightCycle[r1].y -> r2
	com.y <- r2
	color.y <- r2
	LightCycle[r1].color -> r2
	color.data <- r2
	LightCycle[r1].heading -> r2
	ADD r2, r2, #24
	com.data <- r2
	MOV r2, #2
	comPtr <- r2
	MOV r2, #1
	color.mode <- r2
	MOV r2, #0
	com.mode <- r2

	addr.vidAddr -> r1
	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2
	MOV r2, #1
	VideoDevice[r1].status <- r2
	MOV r2, #0
	do
		VideoDevice[r1].status -> r3
	while(r2 != r3)

	goto :return:
:loadDeviceDescriptors:
	;call findDeviceMod1, locate device with hash -1859106108 (VideoDevice) and store the address in mod1
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:retm:
	stackPtr& <- r1
	64: MOV r1, #-2068042898
	mod1 <- r1
	goto :findDeviceMod1:
	:retm:

	;store VideoDevice address in vidAddr
	mod1 -> r1
	addr.mouseAddr <- r1

	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:retrtc:
	stackPtr& <- r1
	64: MOV r1, #-1017264500
	mod1 <- r1
	goto :findDeviceMod1:
	:retrtc:

	;store VideoDevice address in vidAddr
	mod1 -> r1
	addr.rtcAddr <- r1

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
	addr.vidAddr <- r1

	;call findDeviceMod1, locate device with hash -2043936910 (InterruptController) and store the address in mod1
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret5:
	stackPtr& <- r1
	64: MOV r1, #-2043936910
	mod1 <- r1
	goto :findDeviceMod1:
	:ret5:

	;store VideoDevice address in vidAddr
	mod1 -> r1
	addr.intAddr <- r1

	;call findDeviceMod1, locate device with hash 1377772586 (KeyboardDevice) and store the address in mod1
	stackPtr -> r1
	ADD r1, r1, #sizeof(int)
	stackPtr <- r1
	ADD r1, r15, #:ret6:
	stackPtr& <- r1
	64: MOV r1, #1377772586
	mod1 <- r1
	goto :findDeviceMod1:
	:ret6:

	;store VideoDevice address in vidAddr
	mod1 -> r1
	addr.keyAddr <- r1

	goto :return:
:findDeviceMod1:
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
	mod1 -> r2
	do
		DeviceEntry[r3].deviceHash -> r1
		ADD r3, r3, #sizeof(DeviceEntry)
	while(r2 != r1)

	SUB r3, r3, #sizeof(DeviceEntry)

	;load mod1 with the relevant address
	DeviceEntry[r3].deviceAddress -> r1
	mod1 <- r1
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

