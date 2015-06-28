#mode defines

#mode data
  int ivt
  int tmp
#mode text
  ivt* -> r12
  64: ADD r1, r15, #:int:
  ivt <- r1
  SVC #0
  tmp -> r1
  HLT
:int:
  MOV r1, #8989
  tmp <- r1
  RET
#data
