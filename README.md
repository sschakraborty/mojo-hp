# mojo-hp
## Mini Online Judge Operator (High Peformance)

The Mini Online Judge Operator (MOJO) is a high performance **online coding judge platform** developed and maintained by **Subhadra Sundar Chakraborty** (<a href="https://github.com/sschakraborty">@sschakraborty</a>) and **Suvaditya Sur** (<a href="https://github.com/x0r19x91">@x0r19x91</a>). MOJO is built on a custom high performance architecture developed by <a href="https://github.com/sschakraborty">@sschakraborty</a> that makes it massively scalable and distributable over hundreds of nodes. The codes are judged on multiple nodes in parallel in a cluster. The underlying mechanisms are completely non-blocking, asynchronous and event-driven which add to the performance.

The core of the judge is natively written on top of Linux kernel API (Linux 2.6+) and is developed and maintained by <a href="https://github.com/x0r19x91">@x0r19x91</a>. The core is asynchronous and highly capable of matching the speeds required for a performance critical distributed online judge.

The latest version is MOJO 1.1. Works for some patches and improvements are being done. These would make way in MOJO 1.1.1.

For any support / contribution contact me at sschakraborty@hotmail.com
