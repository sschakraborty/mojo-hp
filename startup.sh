#! /bin/bash
# The MOJO 1.1.1 starter script

# check for config file

echo "MOJO: Mini Online Judge Operator v1.2.0"
echo "Please wait while MOJO boots up..."

if [ -e "LICENSE" -a -e "mojo-hp.sql" ]
then
	if [ -e "./out/artifacts/mojo_hp_jar/mojo-hp.jar" ]
	then
		java -Xms128m -Xmx1g -server -jar ./out/artifacts/mojo_hp_jar/mojo-hp.jar
	else
		echo "Some vital JAR files are missing please re-download and try again"
	fi
else
	echo "Missing LICENSE or DB design (mojo-hp.sql) file"
fi
