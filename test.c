#include <stdio.h>

#define derp = 3

#ifndef derp

#define test = 5

int main(void)
{
	return derp;
}
#endif

int main(void)
{
}
