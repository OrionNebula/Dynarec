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

#mode text
	
	MOV r1, #20480
	SUB r1, r1, #sizeof(VideoDevice)
	
	MOV r4, #0
	MOV r5, #64
	MOV r6, #32

	ADD r2, r15, #:@com@:
	VideoDevice[r1].instAddr <- r2
	ADD r7, r6, #1
	MUL r7, r7, r5
	LBTR r7, r2, #0
	ADD r2, r2, #4
	
	:extern:

	MOV r5, #64
	SUB r6, r6, #1

	:loop:

	SUB r5, r5, #1

	64: MOV r3, #4874372
	GraphicsCommand[r2].color <- r3
	ADD r3, r5, #33
	ADD r3, r3, r6
	GraphicsCommand[r2].char <- r3
	GraphicsCommand[r2].x <- r5
	GraphicsCommand[r2].y <- r6
	
	ADD r2, r2, #sizeof(GraphicsCommand)
	64: B #:loop:, r5, r4, #4

	64: B #:extern:, r6, r4, #4

	MOV r3, #1
	VideoDevice[r1].status <- r3

	HLT
	:@com@:
	[4]
	[sizeof(GraphicsCommand)]
