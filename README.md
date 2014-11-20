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

This is a work in progress, and likely to have problems at the moment.
Patches, bug reports welcome!
