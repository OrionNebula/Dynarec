? AL = #0 ?
? NE = #1 ?
? LT = #2 ?
? EQ = #3 ?
? GT = #4 ?
? LE = #5 ?
? GE = #6 ?

	ADD r0, r15, #:endLbl:

	MOV r1, #0
	MOV r2, #64
:loop:
BITS64
	MOV r3, #15613952
BITS32
	LBTR r3, r0, #4
	ADD r3, r1, #65
	BTR r3, r0, #8
	LSL r3, r1, #16
	LBTR r3, r0, #9

	ADD r1, r1, #1
	ADD r0, r0, #9

BITS64
	B #:loop: r1, r2, LT
BITS32

	ADD r1, r15, #:endLbl:
	LBTR r2, r1, #0

	MOV r0, #1024
	SUB r0, r0, #13

	LBTR r1, r0, #9

	BFR r1, r0, #0
	ORR r1, r1, #1
	BTR r1, r0, #0

	HLT
:endLbl:
