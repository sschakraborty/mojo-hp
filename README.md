# mojo-hp
## Mini Online Judge Operator (High Peformance)

The Mini Online Judge Operator (MOJO) is a high performance **online coding judge platform** developed and maintained by **Subhadra Sundar Chakraborty** (<a href="https://github.com/sschakraborty">@sschakraborty</a>) and **Suvaditya Sur** (@x0r19x91). MOJO is built on a custom high performance architecture developed by @sschakraborty that makes it massively scalable and distributable over hundreds of nodes. The codes are judged on multiple nodes in parallel in a cluster. The underlying mechanisms are completely non-blocking, asynchronous and event-driven which add to the performance.

The front-end, back-end, data and code pipelines are written on top of Vert.x 3.5.0. Refer to http://vertx.io/.

The core of the judge is natively written on top of Linux kernel API (Linux 2.6+) and is developed and maintained by @x0r19x91.
The core is asynchronous and highly capable of matching the speeds required for a performance critical distributed online judge.

This app is in Fourth Phase of development and would make an initial release soon.

For any support / contribution contact me at sschakraborty@hotmail.com
