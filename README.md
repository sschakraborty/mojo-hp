# mojo-hp
## Mini Online Judge Operator (High Peformance)

## Project is deprecated and archived
## This project will be succeeded by project KJudge @ https://github.com/sschakraborty/KJudge

The Mini Online Judge Operator (MOJO) is a high performance **online coding judge platform** developed and maintained by **Subhadra Sundar Chakraborty** (<a href="https://github.com/sschakraborty">@sschakraborty</a>) and **Suvaditya Sur** (<a href="https://github.com/x0r19x91">@x0r19x91</a>). MOJO is built on a custom high performance architecture developed by <a href="https://github.com/sschakraborty">@sschakraborty</a> that makes it massively scalable and distributable over hundreds of nodes. The codes are judged on multiple nodes in parallel in a cluster. The underlying mechanisms are completely non-blocking, asynchronous and event-driven which add to the performance.

The core of the judge is natively written on top of Linux kernel API (Linux 2.6+) and is developed and maintained by <a href="https://github.com/x0r19x91">@x0r19x91</a>. The core is asynchronous and highly capable of matching the speeds required for a performance critical distributed online judge.

The latest version is 1.2.0. MOJO 1.2.0 is the first MOJO version that features a fully functional high-performance native core at it's heart with option for vanilla core as well.


To use MOJO:
<ul>
	<li>Pre-requisites</li>
	<ul>
		<li>MOJO runs on all flavors of Linux OS (Kernel v2.6+).</li>
		<li>Currently there is no plan for supporting Windows.</li>
		<li>Java 8 and MySQL 5.6+ / MariaDB 10.1+ must be installed.</li>
	</ul>
	<li>Download the latest release of MOJO as ZIP.</li>
	<li>Unpack ZIP file in some directory.</li>
	<li>Update the <b>config.json</b> file as follows</li>
	<ul>
		<li>The database object is for connecting to the database.</li>
		<li>Update the database username / password / database name etc. properly.</li>
		<li>Change the security key to a <b>long random alphanumeric string with special characters</b>.</li>
		<li>This string serves as salt value for encrypting keys throughout the application.</li>
		<li>Changing the key is very highly recommended. This key has to be kept secret for security reasons.</li>
		<li>Finally update the judge Test folder. The Test folder is a folder where all the submitted codes, their compiled binaries (if applicable) and output files are stored for comparison purpose and also for future reference.</li>
		<li>The Test folder must be a blank valid folder on the system. One can keep the default Test folder as included with MOJO release (just update the proper path).</li>
	</ul>
	<li>Finally execute the <b>./startup.sh</b> script to start MOJO.</li>
	<li>Enter the valid configuration file (.json) path.</li>
	<li>If everything was alright, MOJO must start up. Open up http://127.0.0.1:12400/ on browser to see MOJO in action.</li>
</ul>

<br>
For any support / contribution contact me at sschakraborty@hotmail.com
