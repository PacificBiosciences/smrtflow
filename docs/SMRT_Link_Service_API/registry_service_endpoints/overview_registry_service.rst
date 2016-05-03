Overview of Registry Service
============================

Use Cases
---------

The Registry Service enables registering instruments with SMRT Link, editing instrument settings,
viewing the instruments registered with SMRT Link, redirecting requests to specific instruments,
and unregistering the instruments.

The main use case will be getting the list of all instruments registered with SMRT Link.

Key Concepts
------------

Registry Service uses a generic concept of *resource*.

**Resource** in its specific meaning is an instrument registered with SMRT Link.

Registry Service provides a generic set of operations over resources,
which can be translated into their specific meanings as follows:

-  create a resource - register an instrument with SMRT link;

-  update a resource - edit settings of an instrument registered with SMRT link;

-  list all resources - get the list of all instruments registered with SMRT Link;

-  fetch a resource by its user-defined Id - view settings of a specific instrument identified by User Id;

-  fetch a resource by its server-provided UUID - view settings of a specific instrument identified by Server UUID;

-  redirect a request to a resource - forward request to a specific instrument registered with SMRT link;

-  delete a resource - unregister an instrument with SMRT link.


