//Test.sls
	#import <stdio.slh>;
	int test, main, five;
	#export test, main;

	main := () -> {
		return test(5);
	};

	test := (int derp) -> {
		return derp + (five += derp);
	};