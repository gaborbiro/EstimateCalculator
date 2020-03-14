# EstimateCalculator

In order to run console jar files in Windows with double-click, run these commands as Administrator:

>\> ftype myjarfile="c:\Program Files\Java\jdk-13.0.2\bin\java.exe" -jar "%1" %*

>\> assoc .jar=myjarfile

Adjust input parameters in input.json.

Command line:

> java -jar estimate_calculator.jar your_input.json
