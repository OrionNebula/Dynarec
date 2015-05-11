? AL = #0 ?
? NE = #1 ?
? LT = #2 ?
? EQ = #3 ?
? GT = #4 ?
? LE = #5 ?
? GE = #6 ?

	ADD r12, r15, #:IVT:
	ADD r0, r15, #:exec:
	LBTR r0, r12, #0
:loop:
	MOV r0, r0
BITS64
	B #:loop: r0, r0, AL
BITS32
	:IVT:
	0x0
	:exec:
	MOV r0, #1024
	SUB r0, r0, #13

	BFR r2, r0, #0
	MOV r3, #0

BITS64
	B #:hlt: r2, r3 NE
BITS32

	ADD r0, r15, #:end:

	MOV r2, #1
	LBTR r2, r0, #0

BITS64
	MOV r2, #15613952
BITS32
	LBTR r2, r0, #4

	MOV r2, #65
	BTR r2, r0, #8

	LBTR r1, r0, #9
	ADD r1, r1, #1

	MOV r2, #1024
	SUB r2, r2, #13

	LBTR r0, r2, #9

	BFR r3, r2, #0
	ORR r3, r3, #1
	BTR r3, r2, #0
	:hlt:
	RET
	:end:
