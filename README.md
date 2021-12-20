
[32 Bit]: docs/32.md
[64 Bit]: docs/64.md


# Dynarec

A realtime dynamic recompiler in Java.

Translates binary assembly into Java bytecode<br>
which is loaded and executed as needed.

Includes a `Custom Instruction Set` and<br>
`Assembly Language` with inspiration from<br>
**C** and **ARM** processors.

The default processor is the `APPLEDRCx64`,<br>
which this documentation describes.

---

**Instruction Sets: ⸢ [32 Bit] ⸥ ⸢ [64 Bit] ⸥**

---

## Assembly

The assembly language used for the `APPLEDRCx64`<br>
is more advanced than other forms of assembly.

**The language**<br>
➜ allows for direct opcode manipulation.<br>
➜ handles memory access for pointers / structures.<br>
➜ handles conditionals with if blocks and comparison operators.<br>
➜ uses a defined syntax for loops.

---

## Sections

`APPLEDRCx64` assembly files are divided into 5 sections.

A section can be declared with `#mode [mode]`

#### Defines

In this section descriptors and names for data structures are declared.

Structures play a critical role in managing systems and memory.

Each structure can be composed of multiple primitives and other structures.



**Example of the Graphics Controller**

```
#mode defines

VideoDevice
{
    byte status
    int width
    int height
    int instAddr
}
```

*Primitive types are builtin structures with a singular*<br>
*property, `value`, that allows accessing it from any address.*

*Int uses `L@int`, long uses `L@long`,..*

#### Data

The data section consists of raw variable names and types<br>
which are given explicit space in the assembled binary and<br>
can be referenced within the code.

Variables declared in the data section will be placed at the next<br>
`#data` tag in the `text` section, which must be explicitly placed.


```
#mode data

int stackPointer
int[16] returnStack
DeviceEntry[16] entry
```

These declarations can also be assigned with expressions containing<br>
**Arithmetic Operations** and previously declared variables.

```
int a = 20
int b = 4 * a
```

The data section also supports the `#toss` tag which will exclude any<br>
previous variable declarations from being included in the compiled binary.

Declarations before the tag effectively behave like compile time calculations.

```
int sector_count = 24
int sector_size = 512

#toss

int diskSize = sector_count * sector_size
```

The assembled code will effectively contain:

```
int diskSize = 12288
```

#### Text

This section contains the main code like raw opcodes<br>
mnemonics as well as language specific constructs.

**Examples**

Storing the address of `aVariable` into register 1

`aVariable * -> r1`

<br>

Storing the value of register 1 into the property<br>
`status` of a `VideoDevice` structure beginning<br>
at the address described by register 1.

`VideoDevice[r1].status <- r1`

<br>

Storing the number `12` into register 1.

```
MOV r1 , #12
```

<br>

Storing the value of register 1 one into the<br>
address described by the value of `aVariable`.

```
aVariable & <- r1
```

#### Macro

Macros are code block templates that have any of their<br>
invocations replaced with the literal content of it's block.

**Declaration**

```
#mode macro

WriteRegister(@Register,@Value)
{
	MOV @Register , #@Value
}
```

**Usage**

```
WriteRegister(r1,45)
```

**Precompiled Code**

```
MOV r1 , #45
```

*Macros can contain any valid text mode*<br>
*structure as well as other macro calls.*

#### Imports

The import section allows for the specification of header<br>
files that will be included in the scope of the current file.

This can be used to bundle structure definitions into<br>
dedicated files, often labeled with the `.asmh` file extension.

These header files can contain any section type, except for `text`.

A headers `data` section is automatically prepared<br>
with `#toss` and thus only used at compile-time.

---

## Control Structures

`APPLEDRCx64` assembly provides 3 major<br>
control structures, `goto`, `if` and `do-while`.

These can be used with the help of **Labels**,<br>
tags which anchor a point in the assembly.


```
MOV r2 , #5
MOV r1 , #0

do
    SUB r2 , r2 , #1
while(r1 != r2)

if(r1 = r2) goto :TestLabel:

HLT

:TestLabel:

if(r1 = r2)
    SUB r1 , r1 , #1
end

goto :TestLabel:
```

*References made to these tags in number literals will be*<br>
*replaced with the distance from that instruction to the label.*

*This is used to obtain absolute pointers tot relative objects*<br>
*like variables as well as to perform accurate jumps.*

---

## Static Linking

As a second stage of compilation, `APPLEDRCx64` allows for static linking.

First files are assembled to the `ACCessible Executable DEFinition`<br>
format which tracks **Imports / Exports** of variables declared with the<br>
`import` & `extern` keywords.

**Any** variable type can be **exported**.<br>

Only `int` may be **imported** as imported<br>
variables are expressed by pointers.


**LinkableFile.asm**

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

**MasterFile.asm**

```
#mode imports

	src.asm/stdlib.asmh

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

In this example `MasterFile.asm` is the main file.

Once both files are compiled and linked, the assembled<br>
content of `MasterFile` will be placed / executed first.
