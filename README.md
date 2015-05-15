# Dynarec
A realtime dynamic recompiler in Java. Translates binary assembly code into Java bytecode which is loaded and executed as needed. Includes a custom instruction set and assembly language with inspiration from C and ARM processors. The default processor is the APPLEDRCx64, which this documentation describes.

## Instruction Set
###32 bit instructions
- MOV Rres, #/Rval
 - Stores the value represented by *val* into the register *Rres*
- ADD Rres, Rarg1, #/Rval
 - Adds *Rarg1* and the value at *val* and stores the result into register *Rres*
- SUB Rres, Rarg1, #/Rval
 - Subtracts the value at *val* from register *Rarg1* and stores the result into register *Rres*
- MUL Rres, Rarg1, #/Rval
 - Multiples *Rarg1* by the value represented by *val* and stores the result into register *Rres*
- DIV Rres, Rarg1, #/Rval
 -  Divides *Rarg1* by the value represented by *val* and stores the result into register *Rres*
- ASR Rres, Rarg1, #/Rval
 - Arithmetically shifts *Rarg1* *val* bits to the right, storing the result in register *Rres*
- LSL Rres, Rarg1, #/Rval
 - Logically shifts *Rarg1* *val* bits to the left, storing the result in register *Rres*
- LSR Rres, Rarg1, #/Rval
 - Logically shifts *Rarg1* *val* bits to the right, storing the result in register *Rres*
- IFD Rarg
 - Integer From Double, truncates the decimal and keeps the integer part
- DFI Rarg
 - Double From Integer, converts an integer into a double of equivalent value
- RTR Rarg, Raddr, #/Roff
 - Register To Ram, stores long value *Rarg* to address *Raddr* + *off*
- RFR Rres, Raddr, #/Roff
 - Register From Ram, loads long value from adress *Raddr* + *off* into register *Rres*
- BTR Rarg, Raddr, #/Roff
 - Byte To Ram, stores the least significant byte of *Rarg* to address *Raddr* + *off*
- BFR Rres, Raddr, #/Roff
 - Byte From Ram, loads byte value from adress *Raddr* + *off* into register *Rres*
- HBTR Rarg, Raddr, #/Roff
 - High Bytes To Ram, stores the most significant 4 bytes of *Rarg* to address *Raddr* + *off*
- LBTR Rarg, Raddr, #/Roff
 - Low Bytes To Ram, stores the least significant 4 bytes of *Rarg* to address *Raddr* + *off*
- HBFR Rres, Raddr, #/Roff
 - Half Bytes From Ram, loads 4 bytes from address *Raddr* + *off* into register *Rres*
- HLT
 - Halts the processor, only recoverable by interrupt
- AND Rres, Rarg1, #/Rval
 - Performs a bitwise and on *Rarg1* and the value at *val* and stores the result into register
- ORR Rres, Rarg1, #/Rval
 - Performs a bitwise or on *Rarg1* and the value at *val* and stores the result into register
- XOR Rres, Rarg1, #/Rval
 - Performs a bitwise exclusive or on *Rarg1* and the value at *val* and stores the result into register
- RET
 - Returns the processor from an interrupted state

###64 bit instructions
- 64: MOV Rres, #/Rarg
 - Stores 32-bit value *arg* into register *res*, intended for literals
- 64: DMOV Rres, #/Rarg
 - Stores floating-point value *arg* into register *res*
- 64: DADD Rres, Rarg1, #/Rarg2
 - Adds *Rarg1* and *arg2* and stores the result in register *Rres*; Done as floating point types
- 64: DSUB Rres, Rarg1, #/Rarg2
 - Subtracts *arg2* from *Rarg1* and stores the result in register *Rres*; Done as floating point types
- 64: DMUL Rres, Rarg1, #/Rarg2
 - Multiplies *Rarg1* by *arg2* and stores the result in register *Rres*; Done as floating point types
- 64: DDIV Rres, Rarg1, #/Rarg2
 - Divides *Rarg1* by *arg2* and stores the result in register *Rres*; Done as floating point types
- 64: B Roff, Rcmp1, Rcmp2, #cmpType
 - Executes a jump to an adress *off* bytes away, assuming *Rcmp1* and *Rcmp2* pass the condition described by *cmpType*
- 64: DLN Rres, #/Rarg
 - Takes the natural log of *arg* and stores the result into *Rres*

#Assembly Language
The assembly language for the APPLEDRCx64 is more advanced than other forms of assembly. It allows direct opcode manipulation, but memory access is handled by pointers and structures, conditionals are handled with if blocks and comparison operators, and loops have a defined syntax.

##File Modes
APPLEDRCx64 assembly files are divided into 3 modes: defines, data, and text, each invoked with "#mode [mode]". Each one expects different information to follow.

###Defines
The defines mode contains descriptors and names for data structures. Each structure can be composed of a series of named primitives or other structures. As an example, here is the control structure for the APPLEDRCx64 graphics controller:

```
VideoDevice
{
	byte status
	int width
	int height
	int instAddr
}
```

These structures play a critical role in managing systems and memory.

###Data
The data mode is a series of raw variable names and types. These variables are given explicit space in the assembled binary and can be referenced within the code. Here is an example of a data section using various types of variables:

```
int stackPtr
int[16] returnStack
DeviceEntry[16] ent
```
This section consists of a single integer named `stackPtr`, a 16-type wide array of integers called `returnStack`, and a 16-type wide array of the custom structure `DeviceEntry` called `ent`. Information created in a data section will be placed at the next `#data` tag found within a text section. This tag must be explicitly placed.

###Text
The text section contains the actual code for any assembly program. This section can contain raw opcode mnemonics or a series of language-specific constructs. This section is where the types and variables defined in the previous sections become useful. This code sample shows how easy it is to manipulate variables and structures in a text section:

Store the address of 'stackPtr' into register 1

`stackPtr* -> r1`

Store the value of register 1 into the property 'status'of a VideoDevice structure beginning at the address described by register 1

`VideoDevice[r1].status <- r1`

Store the number 12 into register 1. Store the value of register one into the adress described by the value of "stackPtr"
```
MOV r1, #12
stackPtr& <- r1
```
##Control Structures
APPLEDRCx64 assembly has 3 major control structures: goto, if, and do-while. These are made possible through the use of labels, small tags which anchor a point in the assembly. Referneces made to these tags in a number literal will be replaced with the distance from that instruction to the label. This is used to obtain absoulte pointers to relative objects like variables as well as perform accurate jumps. Here's a small code example which employs these structures.

```
MOV r2, #5
MOV r1, #0
do
    SUB r2, r2, #1
while(r1 != r2)
if(r1 = r2) goto :test1:
HLT
:test1:
if(r1 = r2)
    SUB r1, r1, #1
end
goto :test1:
```
