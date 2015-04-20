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
		byte mode
		int data
		byte x
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

		64: MOV r3, #1000000
		SUB r3, r3, #sizeof(VideoDevice)
		SUB r3, r3, #sizeof(IntCtrl)
		SUB r2, r3, #sizeof(Keyboard)

		IntCtrl[r3].toMonitor <- r2
		MOV r2, #1
		IntCtrl[r3].regionLength <- r2
		IntCtrl[r3].status <- r2

	HLT
	:keyInterrupt:
		64: MOV r2, #1000000
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
			MUL r3, r4, #sizeof(Keystroke)
			strokePointer* -> r7
			ADD r3, r3, r7
			Keystroke[r3].char -> r8
			MOV r7, #0
			if(r7 = r8) goto :endLoop:
			Keystroke[r3].key -> r7
			MOV r0, #14
			if(r0 = r7)
				SUB r1, r1, #1
				MOV r8, #0
			end

			com.data <- r8
			com.x <- r1
			com.y <- r9

			MOV r7, #1
			graphLen <- r7
			64: MOV r2, #1000000
			SUB r2, r2, #sizeof(VideoDevice)
			VideoDevice[r2].status <- r7

			Keystroke[r3].key -> r7
			MOV r0, #14
			if(r0 = r7)
				SUB r1, r1, #1
				MOV r0, #0
				if(r1 < r0)
					MOV r1, #62
					SUB r9, r9, #1
				end
			end

			do
				VideoDevice[r2].status -> r3
				MOV r6, #0
			while(r6 != r3)

			ADD r1, r1, #1
			MOV r8, #64
			if(r1 >= r8)
				MOV r1, #0
				ADD r9, r9, #1
			end
			:endLoop:
			ADD r4, r4, #1
		while(r5 > r4)

	RET
	#data
