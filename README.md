# mojo-hp
## Mini Online Judge Operator (High Peformance)

The Mini Online Judge Operator (MOJO) is a high performance **online coding judge platform** developed and maintained by **Subhadra Sundar Chakraborty** (<a href="https://github.com/sschakraborty">@sschakraborty</a>) and **Suvaditya Sur** (<a href="https://github.com/x0r19x91">@x0r19x91</a>). MOJO is built on a custom high performance architecture developed by <a href="https://github.com/sschakraborty">@sschakraborty</a> that makes it massively scalable and distributable over hundreds of nodes. The codes are judged on multiple nodes in parallel in a cluster. The underlying mechanisms are completely non-blocking, asynchronous and event-driven which add to the performance.

The core of the judge is natively written on top of Linux kernel API (Linux 2.6+) and is developed and maintained by <a href="https://github.com/x0r19x91">@x0r19x91</a>. The core is asynchronous and highly capable of matching the speeds required for a performance critical distributed online judge.

The latest version is MOJO 1.1. Works for some patches and improvements are being done. These would make way in MOJO 1.1.1.

To use MOJO:
	- Pre-requisites
		- MOJO runs on all flavors of Linux OS (Kernel v2.6+).
		- Currently there is no plan for supporting Windows.
		- Java 8 and MySQL 5.6+ / MariaDB 10.1+ must be installed.
	- Download the latest release of MOJO as ZIP.
	- Unpack ZIP file in some directory.
	- Update the config.json file as follows
		- The database object is for connecting to the database.
		- Update the database username / password / database name etc. properly.
		- Change the security key to a long random alphanumeric string with special characters.
		- This string serves as salt value for encrypting keys throughout the application.
		- Changing the key is very highly recommended. This key has to be kept secret for security reasons.
		- Finally update the judge Test folder. The Test folder is a folder where all the submitted codes, their compiled binaries (if applicable) and output files are stored for comparison purpose and also for future reference.
		- The Test folder must be a blank valid folder on the system. One can keep the default Test folder as included with MOJO release (just update the proper path).
	- Finally execute the ./startup.sh script to start MOJO.
	- Enter the valid configuration file (.json) path.
	- If everything was alright, MOJO must start up. Open up http://127.0.0.1:12400/ on browser to see MOJO in action.

For any support / contribution contact me at sschakraborty@hotmail.com
