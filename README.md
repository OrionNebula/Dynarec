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
- SVC #int
 - Triggers interrupt *int*

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
- 64: B #/Roff, Rcmp1, Rcmp2, #cmpType
 - Executes a jump to an adress *off* bytes away, assuming *Rcmp1* and *Rcmp2* pass the condition described by *cmpType*
- 64: DLN Rres, #/Rarg
 - Takes the natural log of *arg* and stores the result into *Rres*

#Assembly Language
The assembly language for the APPLEDRCx64 is more advanced than other forms of assembly. It allows direct opcode manipulation, but memory access is handled by pointers and structures, conditionals are handled with if blocks and comparison operators, and loops have a defined syntax.

##File Modes
APPLEDRCx64 assembly files are divided into 5 modes: defines, data, text, macro, and imports, each invoked with "#mode [mode]". Each one expects different information to follow.

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

These structures play a critical role in managing systems and memory. Each primitive type has a built in structure with one property, `value`, that allows you to access a primitive type at any address. Ints use `L@int`, longs `L@long`, and so on.

###Data
The data mode is a series of raw variable names and types. These variables are given explicit space in the assembled binary and can be referenced within the code. Here is an example of a data section using various types of variables:

```
int stackPtr
int[16] returnStack
DeviceEntry[16] ent
```
This section consists of a single integer named `stackPtr`, a 16-type wide array of integers called `returnStack`, and a 16-type wide array of the custom structure `DeviceEntry` called `ent`. Information created in a data section will be placed at the next `#data` tag found within a text section. This tag must be explicitly placed.

Placing an equals sign after a primitve type declaration followed by a valid value or expression for that type will assign it that value in the compiled binary. Declaration expressions are just like normal arithmetic expressions, supporting the plus, minus, division, and multiplication operators. Previously declared variables can be included in expressions simply by placing their name in it, just like variables in arithmetic expressions.

The data section supports the `#toss` tag which will abandon any variables up to that point, keeping their calculated values but not including them in the compiled binary. Here's an example of expressions and the `#toss` tag:

```
int DISK_SECTOR_SIZE = 512
int DISK_SECTOR_COUNT = 24
#toss
int diskSize = DISK_SECTOR_SIZE * DISK_SECTOR_COUNT
```

Only diskSize will appear and be usable in the final assembly.

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
###Macro
Macros are code blocks that replace every instance of their invocation with their contents. Here's an example of a macro section entry:
```
WriteRegister(@Register, @Value)
{
	MOV @Register, #@Value
}
```
When called in the code (`WriteRegister(r1, 45)`) the values passed into the macro replace each instance of the argument name in the macro and that altered text takes the place of the invocation and is processed again. Macros can contain any valid text mode structure, including other macro invocations.

###Imports
The imports section allows you to specify header files to include in the scope of that assembly file. This means you can consolidate structure definitions into dedecated files rather than having to re-specify them in a `#mode defines` section for each `.asm` file. Header files typically have the extension `.asmh` and can contain any section besides `#mode text`. Should a header file include a `#mode data` section, the variables declared there will be automatically `#toss`ed and made into expression constants.

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
##Static Linking
APPLEDRCx64 supports static linking as a second stage of compilation. Files are first assembled to the ACCessible Executable DEFinition Format, a format which tracks what variables are to be imported or exported by an assembly file. The `import` and `extern` keywords placed before a variable name allow it to be imported or exported, respectivley. Any variable type can be exported, but only `int` may be imported. This is because imported variables are expressed as pointers. Here's an example of how linking might be used:

LinkableFile.asm
```
#mode imports
	src.asm/stdlib.asmh
#mode data
	import int memSize
	import int returnAddr
	extern int goReturn
	extern byte[0] go
#mode text
	#data
	memSize -> r1
	L@int[r1].value -> r1
	goReturn <- r1
	returnAddr -> r1
	AbsJump(r1)
```
MasterFile.asm
```
#mode imports
	arc.asm/stdlib.asmh
#mode data
	extern int memSize
	import int goReturn
	import int go
	extern byte[0] returnAddr
#mode text
	memSize <- r1
	go -> r1
	AbsJump(r1)
	
	#data
	goReturn -> r1
	L@int[r1] -> r1
	;do something with r1
	HLT
```
MasterFile.asm, as the name implies, is used as the master file in this example. Once these two files are compiled and linked, the assembled content of MasterFile will be placed first. This is what will execute if the linked file was used as the bios.
