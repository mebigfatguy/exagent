exagent
=======

a javaagent to embellish exception stack traces with useful information


Run your application like

java -javaagent:/path/to/exagent.jar -jar your.jar

exagent will dynamically instrument your code to change the 
information contained in exception stack traces. Specifically, it will
add the values of all method params of all methods called on the stack 
at the time the method was invoked for that exception stack trace.
This allows you to have more information to track down what went wrong.

There are several options that you can specify when attaching the agent:

    * exclusion_pattern
    * inclusion_pattern
    * parm_size_limit
    
The first two are regular expressions that specify what classes/packages
will be instrumented. Only classes that are not eliminated by the
exclusion_pattern but are added by the inclusion_pattern are instrumented.
Of course either (or both) can be not specified.

The parm_size_limit limits the size of the output of any particular parameter
to n characters, in the case that the toString() call of one of your parameters
is large. It also can be not specified.

To specify these attributes add them to the -javaagent specification at the end
following an equals sign, like this:

java -javaagent:/path/to/exagent.jar=exclusion_pattern=/org/*;inclusion_pattern=/org/mydomain/*;parm_size_limit=100 -jar your.jar
  
This is a work in progress, and likely to have problems at the moment.
Patches, bug reports welcome!
