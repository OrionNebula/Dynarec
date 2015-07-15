#mode imports
  src.asm/stdlib.asmh
#mode data
  import int memSize
  extern byte[0] go
#mode text
  #data
  memSize& -> r5
  HLT
