# ThreeColumnTableTransposition
CSV-to-CSV transposition of three-column tables<br>
This project builds runnable jar<br>
App could be run without params, and it would show a simple GUI.<br>
App could be run with `java -jar ThreeColumnTableTransposition-blablabla.jar src\main\resources\example.csv`
to get logging to console.<br>
If you have troubles converting some file in GUI mode - run app in a console against one file to look for errors.

##Description
You could get a 3-column csv-file, give it to this transposition app, and it will make
a transposition variant.

Source table should contain 3 columns:
* time group marker - e.g. 2019-03, 2019-04, 2019-05 if you want original data be divided by months
or 2019-03-01, 2019-03-02, 2019-03-03... if data divided by days, etc.
* value, which is grouped. For example, it could be url paths. 
* long number, presenting count of grouped values for some time group marker
* data separator - a space 

Result would contain:
* unique values from second column would go as column with grouped values
* time markers would go as first *row*
* longs would go as data in between
* also it would compute two averages: (avgByAll) average by num of unique time group markers
and (avgByNE) average of not empty counts divided by num of present values
* data would be sorted by avgByAll (descending)
* data separator - ';'

##Example
You could see a file in sources: src\main\resources\example.csv

Source:

"time_markers" "values" "counts"<br>
2019-01 one 28432<br>
2019-01 two 2432<br>
2019-02 one 28432<br>
2019-02 two 2132<br>
2019-02 three 21132<br>
2019-03 two 2472<br>
2019-03 three 12432<br>
2019-03 four 1

Results:

value;avgByAll;avgByNE;2019-01;2019-02;2019-03<br>
one;18954,666666667;28432,000000000;28432;28432;<br>
three;11188,000000000;16782,000000000;;21132;12432<br>
two;2345,333333333;2345,333333333;2432;2132;2472<br>
four;0,333333333;1,000000000;;;1
