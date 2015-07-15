#mode imports
  src.asm/stdlib.asmh
#mode data
  import int go
  extern int memSize
#mode text
  memSize <- r1
  go -> r1
  AbsJump(r1)
  #data
