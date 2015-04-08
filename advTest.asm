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

#mode text

	MOV r1, #1024
	SUB r1, r1, #len(VideoDevice)

	VideoDevice[r1].width -> r2

	MUL r2, r2, #2
	
	VideoDevice[r1].width <- r2

	HLT
