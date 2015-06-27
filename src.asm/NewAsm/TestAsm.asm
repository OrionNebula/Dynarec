#mode defines
  IVT
  {
    int int0
    int int1
  }
#mode data
  IVT tbl
  int test
#mode text
  tbl* -> r12
  64: ADD r1, r15, #:HLT:
  tbl.int1 <- r1
  SVC #1
  goto :test:
  HLT
:test:
  test -> r1
  HLT
:HLT:
  MOV r1, #8989
  test <- r1
  RET
#data
