#mode defines
	VideoDevice
	{
		byte status
		int width
		int height
		int instAddr
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

	GraphicsCommand
	{
		byte mode
		int data
		byte x
		byte y
	}

	MouseDevice
	{
		byte status
		int x
		int y
	}

	Keystroke
	{
		byte key
		byte char
	}
#mode data
	int memSize

	int returnStack
	int[20] returnData

	int testStack
	byte[256] testData

	int comPtr
	GraphicsCommand com

	int[4] param
	byte endPtr
#mode text
	memSize <- r1

	MOV r1, #0
	returnStack <- r1
	testStack <- r1

	MOV r1, #12
	param[0] <- r1

	returnStack* -> r1
	returnStack -> r2
	ADD r3, r2, #1
	returnStack <- r3
	ADD r1, r1, #sizeof(int)
	MUL r2, r2, #sizeof(int)
	ADD r1, r1, r2
	MOV r2, #12
	ADD r2, r2, r15
	L@int[r1].value <- r2
	goto :push:

	returnStack* -> r1
	returnStack -> r2
	ADD r3, r2, #1
	returnStack <- r3
	ADD r1, r1, #sizeof(int)
	MUL r2, r2, #sizeof(int)
	ADD r1, r1, r2
	MOV r2, #12
	ADD r2, r2, r15
	L@int[r1].value <- r2
	goto :displayStack:

	;History is made!

	MOV r2, #0
	com.x <- r2
	MOV r2, #1
	com.y <- r2

	MOV r2, #65
	com.data <- r2

	MOV r2, #1
	comPtr <- r2
	VideoDevice[r1].status <- r2

	do
		VideoDevice[r1].status -> r2
	while(r3 != r4)

	HLT
:push:
	testStack* -> r1
	testStack -> r2
	ADD r3, r2, #1
	testStack <- r3
	ADD r1, r1, #sizeof(int)
	MUL r2, r2, #sizeof(int)
	ADD r1, r1, r2
	param[0] -> r2
	L@int[r1].value <- r2

	goto :return:
:pop:
	goto :return:
:return:
	returnStack* -> r2
	returnStack -> r3
	SUB r3, r3, #1
	returnStack <- r3
	ADD r2, r2, #sizeof(int)
	MUL r3, r3, #sizeof(int)
	ADD r2, r2, r3
	L@int[r2].value -> r7

	SUB r2, r7, r15
	SUB r2, r2, #12
	64: B r2, r0, r0, #0

:displayStack:
	memSize -> r1
	SUB r1, r1, #sizeof(VideoDevice)

	comPtr* -> r2
	VideoDevice[r1].instAddr <- r2

	testStack* -> r2
	testStack -> r3
	SUB r3, r3, #1
	testStack <- r3
	ADD r2, r2, #sizeof(int)
	MUL r3, r3, #sizeof(int)
	ADD r2, r2, r3
	L@int[r2].value -> r2
	endPtr* -> r3
	L@int[r3].value <- r2


	MOV r6, #0
	MOV r3, #0
	do
		AND r4, r2, #1

		com.x <- r6
		ADD r6, r6, #1
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

	;SUB r2, r7, r15
	;SUB r2, r2, #12
	;64: B r2, r0, r0, #0

	HLT
	#data
