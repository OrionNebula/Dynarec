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

	Keystroke
	{
		byte key
		byte char
	}

	GraphicsCommand
	{
		int color
		byte char
		byte dX
		byte x
		byte dY
		byte y
	}

	IVT
	{
		int monitorAddr
	}
#mode data
	IVT ivtTable
	int graphLen
	GraphicsCommand com
	byte keyLen
	Keystroke strokePointer
#mode text
		ivtTable* -> r12
		ADD r2, r15, #:keyInterrupt:
		ivtTable.monitorAddr <- r2

		MOV r3, #20480
		SUB r3, r3, #sizeof(VideoDevice)
		SUB r3, r3, #sizeof(IntCtrl)
		SUB r2, r3, #sizeof(Keyboard)

		IntCtrl[r3].toMonitor <- r2
		MOV r2, #1
		IntCtrl[r3].regionLength <- r2
		IntCtrl[r3].status <- r2

	HLT
	:keyInterrupt:
		MOV r2, #20480
		SUB r2, r2, #sizeof(VideoDevice)

		graphLen* -> r3
		VideoDevice[r2].instAddr <- r3

		SUB r2, r2, #sizeof(IntCtrl)
		SUB r2, r2, #sizeof(Keyboard)

		Keyboard[r2].bufferLen -> r3
		Keyboard[r2].readLen <- r3
		keyLen <- r3
		strokePointer* -> r3
		Keyboard[r2].addr <- r3
		MOV r3, #1
		Keyboard[r2].busy <- r3
		MOV r4, #0

		do
			Keyboard[r2].busy -> r3
		while(r4 != r3)

		keyLen -> r5

		do
			64: MOV r3, #0xffffff
			com.color <- r3
			MUL r3, r4, #sizeof(Keystroke)
			strokePointer* -> r7
			ADD r3, r3, r7
			Keystroke[r3].char -> r3
			com.char <- r3
			com.x <- r1
			com.y <- r9

			MOV r3, #1
			graphLen <- r3
			MOV r2, #20480
			SUB r2, r2, #sizeof(VideoDevice)
			VideoDevice[r2].status <- r3

			do
				VideoDevice[r2].status -> r3
				MOV r6, #0
			while(r6 != r3)

			ADD r4, r4, #1
			ADD r1, r1, #1
			MOV r8, #64
			if(r1 >= r8)
				MOV r1, #0
				ADD r9, r9, #1
			end
		while(r5 != r4)

	RET
	#data
