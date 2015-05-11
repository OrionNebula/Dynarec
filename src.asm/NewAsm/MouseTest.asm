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
	int oldX
	int oldY
	int graphLen
	GraphicsCommand com
	Keystroke key
#mode text
	MOV r1, #1024
	SUB r1, r1, #sizeof(VideoDevice)

	MOV r2, #1
	graphLen <- r2
	graphLen* -> r2
	VideoDevice[r1].instAddr <- r2

	SUB r2, r1, #sizeof(IntCtrl)
	SUB r2, r2, #sizeof(Keyboard)

	do
		SUB r2, r2, #sizeof(MouseDevice)

		MOV r5, #1
		MouseDevice[r2].status <- r5
		do
			MouseDevice[r2].status -> r3
		while(r5 = r3)

		oldX -> r3
		com.x <- r3
		VideoDevice[r1].width -> r4
		DIV r4, r4, #64
		MouseDevice[r2].x -> r3
		DIV r3, r3, r4
		oldX -> r4
		if(r4 != r3)
			MOV r5, #0
		end

		oldY -> r3
		com.y <- r3
		VideoDevice[r1].height -> r4
		DIV r4, r4, #32
		MouseDevice[r2].y -> r3
		DIV r3, r3, r4
		MOV r6, #0
		if(r3 = r6)
			MOV r3, #1
		end
		oldY -> r4
		if(r4 != r3)
			MOV r5, #0
		end

		MOV r3, #0
		MOV r4, #1
		if(r5 = r3)
			com.data <- r3
			MOV r3, #1
			VideoDevice[r1].status <- r3
			do
				VideoDevice[r1].status -> r3
			while(r4 = r3)
		end

		VideoDevice[r1].width -> r4
		DIV r4, r4, #64
		MouseDevice[r2].x -> r3
		DIV r3, r3, r4
		com.x <- r3
		oldX <- r3

		VideoDevice[r1].height -> r4
		DIV r4, r4, #32
		MouseDevice[r2].y -> r3
		DIV r3, r3, r4
		MOV r4, #0
		if(r3 = r4)
			MOV r3, #1
		end
		com.y <- r3
		oldY <- r3

		MOV r4, #0
		MOV r3, #24
		com.data <- r3
		MOV r3, #1
		VideoDevice[r1].status <- r3
		do
			VideoDevice[r1].status -> r3
		while(r4 = r3)

		ADD r2, r2, #sizeof(MouseDevice)

		Keyboard[r2].bufferLen -> r3
		MOV r4, #0
		if(r3 > r4)
			key* -> r4
			MOV r3, #1
			Keyboard[r2].readLen <- r3
			Keyboard[r2].addr <- r4
			Keyboard[r2].busy <- r3

			MOV r4, #1
			do
				Keyboard[r2].busy -> r3
			while(r4 = r3)

			key.char -> r3
			com.data <- r3
			com.y -> r3
			SUB r3, r3, #1
			com.y <- r3
			MOV r3, #1
			VideoDevice[r1].status <- r3
		end


	while(r1 = r1)

	HLT
	#data
