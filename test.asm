? AL = #0 ?
? NE = #1 ?
? LT = #2 ?
? EQ = #3 ?
? GT = #4 ?
? LE = #5 ?
? GE = #6 ?

	;ADD r0, r15, #:endLbl:
	BITS64
	MOV r0, r15
	BITS32
	;MOV r1, #:endLbl:
	HLT
	:endLbl:
