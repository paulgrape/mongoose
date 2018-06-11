# Basic Terms

* **Storage**

  A storage which performance is been measured in the *test*. Currently
  several types of storages are supported.

* **Item**

  The unit to account the performance rates. Maybe a file, object,
  directory, bucket, etc. An *Item* has the identifier property (name).

* **Data Item**

  An *Item* with a data payload. Any *Data Item* has the corresponding
  size property.

* **Item Input**

  The readable source of the items. This may be a CSV file, a binary
  stream, a collection or a bucket listing.

* **Item Output**

  The writable destination for the items. This may be a CSV file, a
  binary stream or a collection.

* **I/O Task**

  An I/O task is a item linked with a particular I/O type
  (write/read/delete). Also, any I/O Task has the state and the
  execution result as an extension of this state.

* **Load Step**

  Load step is an unit of metrics reporting and test execution flow.

  For each load step:
  - total metrics are calculated and reported
  - limits are configured and controlled

* **Scenario**

  A set of load steps combined and organized using flow elements
  provided by a scripting engine which supports JSR-223. Mongoose
  invokes the default scenario if no custom scenario is specified. The
  default scenario just runs the single *linear* load step.

# Components

## Storage Driver

Executes the I/O tasks generated by *Load Generator*s.
The basic property is the concurrency level and storage client
configuration. The functionality includes:

* Low-level implementation of the I/O tasks execution functionality
* Rate limit related things
* Callbacks for the completed I/O Tasks

## Load Generator

Load Generator is a component which generates the I/O tasks from the
items got from the input. Many storage drivers may be associated with a
load generator. The basic properties are:

* Origin Index (all I/O tasks generated share the same origin index)
* I/O type (create/read/etc)
* Shared rate throttle
* Shared weight throttle
* Storage drivers list
* Storage drivers balancer

## Load Controller

A load controller is an unit of test step control.
Functionality:

* Configuration deployment
* Test initiation
* Execution control (timeouts handling, shutdown invocations, etc)

## Metrics Manager

Metrics aggregation and representation. The component is a singleton
which was differentiated from the Load Controller component. Many load
controllers may be associated with the single metrics manager.