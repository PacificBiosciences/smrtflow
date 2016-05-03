Overview of Health Service
==========================

Use Cases
---------

The Health Service enables monitoring of the health status of the system.

Key Concepts
------------

| A **Health Gauge** is a measurement of a specific health metric, such as 'disk space full'.
| A Health Gauge is analogous to a smoke detector.
|
| A **Health Message** is a means to alter the state of a gauge.
| It provides a message string that contains context information, as well as the message source, and severity level.


Severity Levels
~~~~~~~~~~~~~~~

Severity Levels list:

-  OK - a health gauge is currently free of problems;

-  CAUTION - equivalent to warning; indicates the detection of a potential or impending service-affecting fault before any significant effects have occurred;

-  ALERT - equivalent to error; indicates that a non-service-affecting condition has developed, and a corrective action should be taken to prevent a more serious fault;

-  CRITICAL - equivalent to fatal failure; indicates that a service-affecting condition, such as a severe degradation in the capability, has developed that requires an urgent corrective action.



