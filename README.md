# diffing-proxy [![Build Status](https://travis-ci.org/not-raspberry/diffing-proxy.svg?branch=travis-setup)](https://travis-ci.org/not-raspberry/diffing-proxy)

or The Hacky Thing.

A service to convert HTTP reponses with full state updates to incremental ones. May be useful
when it's suboptimal to respond with full state to each request but it's affordable to send
the full state the first time client requests it.


## Overview

```

Client 1
Client 2   ←---→   Diffing proxy    ←---→    Backend
Client 3         Serves incremental       Can only serve
Client 4           state updates.        the latest state.
...

Frontend clients
request and apply
state updates.

```

## Roadmap

### Done

* Basic diffing and caching implementation
* Passing headers from the user request to the backend request
* Backend requests timeouts

### To do

* Per-user caching of backend state
* Rate-limiting backend queries
* Better defined data formats, accepting more than just JSONs
* A defined way to apply the diffs (for now you have to know about the ``differ`` library)
* Integration testing - the boot path is uncovered

### Sample session

```

Backend and Diffing Proxy sit quietly on the server, synchronised. Client enters.

Client to Diffing Proxy:
I hold no state. I request the latest one.

Diffing Proxy to Backend:
I already hold the state version 42. Give me the latest state if it updated.

Backend to Diffing Proxy:
No updates.

Diffing Proxy to Client:
Here, grab the state, version 42.

... some time passes ...

Client to Diffing Proxy:
I have state version 42, anything new?

Diffing Proxy to Backend:
I already hold the state version 42. Give me the latest state if it updated.

Backend to Diffing Proxy:
Here's state, version 43.

Diffing Proxy to Client:
You told you have 42, here's the diff between 42 and 43.

```

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start the diffing proxy, run:

    lein run -c config.edn

To start a mock backend app (an app that memorises request bodies of POST
requests and responds with them to GET requests on the same path):

    lein with-profile backend-mock run

## License

Copyright © 2016 not-raspberry (https://github.com/not-raspberry/).
