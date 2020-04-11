# sweet-crud

A Clojure library that provides two complementary macro's for creating routes and SQL database access functions for CRUD operations.


If you use (metosin/compojure-api)[https://github.com/metosin/compojure-api] and a SQL database, you can use the macro's in this library to
create CRUD routes with persistence.

## Usage
First add the dependency:

[![Clojars Project](https://img.shields.io/clojars/v/sweet-crud.svg)](https://clojars.org/sweet-crud)

Build status:
[![Kah0ona](https://circleci.com/gh/Kah0ona/sweet-crud.svg?style=svg)](https://circleci.com/gh/Kah0ona/sweet-crud)


An example. Say you have a SQL table called `customers`, with two fields: `id`, `name**. You want to provide the 4 operations through REST routes, ie. you want these routes:


```
GET /customers
GET /customers/:id
POST /customers
PUT /customers
DELETE /customers/:id
```

You can do this with vanilla `compojure-api`, and, say `clojure.java.jdbc` or `honeysql`, but this is fairly repetetive.

Here's how you do it with this library (also see note below!):

```clojure
(namespace my.namespace
 (:require [sweet-crud.core :refer [with-crud-routes defcrud]]
           [compojure.api.api :as api]))

;; Given a SQL table `customers` with columns `id` and `name`,
;; we can define the database CRUD operations as follows
(defcrud
 ;;singular name, generates functions (find-customer), (find-customers), (create-customers), etc.
  "customer"
  "customers"  ;; name of the SQL table
  :id ;; primary key of the table above
  ;; optional vector of keys that are allowed to be added/updated
  ;; (ie. extra keys that don't map on the database, will be excluded)
  [:id :name])

;; In conjunction (the above is required) we can define routes for it.
(def my-context
   (with-crud-routes
     {:singular "customer"
      ;;optional, defaults to []
      :middleware []}
      ;;; you can add more routes here...
   ))

(def app
  (api/api
    {}
    ;;other routes
    my-context
    ))

```

If you've done this, you've basically got 4 REST endpoints that persist to SQL

**NOTE**: In the current version it is required that there is a database connection
inserted through ring middleware, as the `:db` key in the request.
That is, in the ring request, there should be a key `:db` present, which holds the connection.


## Extra configuration options

Some defaults can be overridden, to give more control:
```clojure
(def my-context-with-more-config
  (with-crud-routes
      {:singular "company"
       :find-fn some-other-find-fn
       :find-by-id-fn some-other-find-by-id-fn
       :update-fn some-other-update-fn
       :create-fn some-other-create-fn
       :delete-fn some-other-delete-fn
       ;; handy for ie. company / companies,
       ;; defaults to adding an 's' to the singular word
       :plural "companies"
       :middleware []
       ;; if the `(defcrud "company" ...)` was done in a different namespace,
       ;; specify it here.
       ;; If you don't use this, but you DO have the `defcrud` call in another namespace,
       ;; make sure you require this namespace with `:refer :all`.
       :database-ns "other.namespace.where.you.called.defcrud"}
      (GET "/some-other-route" {:as request}
          :query-params [query :- s/Str]
          :middleware []
          (do-something query))
        ;;; you can add more routes here...
        ))
```




## License

Copyright © 2020 Marten Sytema

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
