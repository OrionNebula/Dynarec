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
		int color
		byte char
		byte dX
		byte x
		byte dY
		byte y
	}

#mode data
	int len
	GraphicsCommand[0] com
#mode text
	
	MOV r1, #20480
	SUB r1, r1, #sizeof(VideoDevice)
	
	MOV r4, #0
	MOV r5, #64
	MOV r6, #32

	len* -> r2
	VideoDevice[r1].instAddr <- r2
	MUL r7, r6, r5
	len <- r7
	com* -> r2
	
	do

		MOV r5, #64
		SUB r6, r6, #1

		do

			SUB r5, r5, #1

			64: MOV r3, #0xffffff
			GraphicsCommand[r2].color <- r3
			MUL r3, r6, #64
			ADD r3, r3, r5
			GraphicsCommand[r2].char <- r3
			GraphicsCommand[r2].x <- r5
			GraphicsCommand[r2].y <- r6
	
			ADD r2, r2, #sizeof(GraphicsCommand)

		while (r5 > r4)

	while (r6 > r4)

	MOV r3, #1
	VideoDevice[r1].status <- r3

	HLT
	#data
