# EstimateCalculator

In order to run console jar files in Windows with double-click, run these commands as Administrator:

>\> ftype myjarfile="c:\Program Files\Java\jdk-13.0.2\bin\java.exe" -jar "%1" %*

>\> assoc .jar=myjarfile

Adjust input parameters in input.json.

`inputEstimateScenario` can be BEST_CASE, WORST_CASE or REALISTIC

`availabilityRestrictions` can be omitted

`availabilityRestrictions.type` can be 

- START: Restriction dates are start dates, meaning you can only work x hours after a certain date. Up until the earliest date you can work the full `weeklyAvailableHours`
- END: You can only work x hours up until a certain date. After the last date you can work the full `weeklyAvailableHours`

Command line:

> java -jar estimate_calculator.jar your_input.json
