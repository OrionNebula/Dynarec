? AL = #0 ?
? NE = #1 ?
? LT = #2 ?
? EQ = #3 ?
? GT = #4 ?
? LE = #5 ?
? GE = #6 ?

	MOV r0, #1024
	SUB r0, r0, #13
	SUB r0, r0, #7

	SUB r2, r0, #14
	LBTR r2, r0, #2

	MOV r1, #1
	BTR r1, r0, #6
	BTR r1, r0, #0

	ADD r12, r15, #:end:
	ADD r1, r15, #:int:
	LBTR r1, r12, #0

	HLT
	:int:
	MOV r0, #1024
	SUB r0, r0, #13

	SUB r0, r0, #21
	ADD r1, r15, #:vis:
	ADD r1, r1, #7

	MOV r2, #1
	LBTR r2, r0, #6
	LBTR r1, r0, #10
	BTR r2, r0, #1

	:wait:
	BFR r3, r0, #1
BITS64
	B #:wait: r3, r2, EQ
BITS32

	BFR r2, r1, #0
	MOV r1, #28

BITS64
	B #:adv: r2, r1, NE
BITS32


	ADD r5, r5, #1
	MOV r4, #0
	RET

	:adv:
	MOV r1, #14

BITS64
	B #:adv2: r2, r1, NE
BITS32

	SUB r4, r4, #1
	RET

	:adv2:

	ADD r0, r0, #21

	BFR r2, r0, #0
	MOV r3, #0

BITS64
	B #:hlt: r2, r3 NE
BITS32

	ADD r0, r15, #:vis:

	MOV r2, #1
	LBTR r2, r0, #0

BITS64
	MOV r2, #15613952
BITS32
	LBTR r2, r0, #4

	;MOV r2, #65
	;BTR r2, r0, #8

	LSL r4, r4, #16
	ORR r4, r4, r5
	LBTR r4, r0, #9
	ASR r4, r4, #16
	ADD r4, r4, #1

	MOV r2, #1024
	SUB r2, r2, #13

	LBTR r0, r2, #9

	BFR r3, r2, #0
	ORR r3, r3, #1
	BTR r3, r2, #0
	:hlt:
	RET
	:end:
	0x0
	:vis:
