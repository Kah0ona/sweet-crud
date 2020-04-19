(ns sweet-crud.core
  (:require
   [clojure.java.jdbc :as j]
   [honeysql.core :as sql]
   [honeysql.helpers :refer :all]))

(defn convert-numbers-map
  [r]
  (into {}
        (map (fn [[k v]]
               (if (instance? java.math.BigDecimal v)
                 [k (float v)]
                 [k v]))
             r)))

(defn update-by-pk-query
  "Creates a default update query, and returns it using sql/format, ready to pass on to jdbc.
  Does not do any checks; just applies the keys in the map. if a value is nil, it will 'unset' the value in the DB"
  [id-field table record]
  (if (nil? (get record id-field))
    (throw (Exception. (str "no ID field found in the record, should be under the key " id-field)))
    (let [id      (get record id-field)
          set-map (dissoc record id-field)
          res     (-> (update (keyword table))
                      (sset set-map)
                      (where [:= id-field id])
                      sql/format)]
      res)))

(defn find-record-by-id
  [table pk conn id]
  (convert-numbers-map
   (j/get-by-id conn table id pk {})))

(defn find-records-by-criteria
  "criteria should be a honeysql where clause."
  [table where-clauses conn]
  (let [base-query      (-> (select :*)
                            (from (keyword table)))
        formatted-query (-> (partial where base-query)
                            (apply where-clauses)
                            sql/format)]
    (j/query conn formatted-query)))

(defn find-records
  [table conn]
  (j/query conn [(str "SELECT * FROM " table)]))

(defn update-record-in-db!
  "Generic function that updates a record in specified table"
  [table pk conn record]
  (let [query (update-by-pk-query pk table record)]
    (j/execute! conn query)))

(defn update-record!
  "If return is supplied, it will return the value"
  [table pk key-seq conn record & [return?]]
  (assert (not (nil? (get record pk)))
          (str "No primary key found in the record, make sure it contains a key" pk))
  (update-record-in-db! table
                        pk
                        conn
                        (select-keys record (or key-seq (keys record))))
  (when return?
    (find-record-by-id table pk conn (get record pk))))

(defn create-record!
  [table pk key-seq conn record & [return?]]
  (let [res (j/insert! conn table (select-keys record
                                               (or key-seq (keys record))))
        id  (:generated_key (first res))]
    (when return?
      (find-record-by-id table pk conn (or (get record pk)
                                           id)))))

(defn delete-record!
  [table pk conn record-or-id]
  (assert (or
           (number? record-or-id)
           (string? record-or-id)
           (and (map? record-or-id)
                (not (nil? (get record-or-id pk)))))
          (str "No primary key found in the record, make sure it contains a key " pk))
  (j/delete! conn table [(str (name pk) " = ?")
                         (if (map? record-or-id)
                           (get record-or-id pk)
                           record-or-id)]))

(defmacro defcrud
  ([singular table pk]
   `(defcrud ~singular ~table ~pk nil))
  ([singular table pk key-seq]
   (let [id-fn-name     (symbol (str "find-" singular "-by-id"))
         find-fn-name   (symbol (str "find-" singular "s"))
         create-fn-name (symbol (str "create-" singular "!"))
         update-fn-name (symbol (str "update-" singular "!"))
         delete-fn-name (symbol (str "delete-" singular "!"))]
     `(do
        (defn ~find-fn-name
          ([~'conn]
           (find-records ~table ~'conn))
          ([~'conn ~'criteria]
           (find-records-by-criteria ~table ~'criteria ~'conn)))

        (defn ~id-fn-name
          [~'id ~'conn]
          (find-record-by-id ~table ~pk ~'conn ~'id))

        (defn ~create-fn-name
          [~'record ~'conn & [~'return? ~'request]]
          (create-record! ~table ~pk ~key-seq ~'conn ~'record (if (nil? ~'return?) true ~'return?)))

        (defn ~update-fn-name
          [~'record ~'conn & [~'return? ~'request]]
          (update-record! ~table ~pk ~key-seq ~'conn ~'record (if (nil? ~'return?) true ~'return?)))

        (defn ~delete-fn-name
          [~'record ~'conn]
          (delete-record! ~table ~pk ~'conn ~'record))))))

(defmacro with-crud-routes
  [{find-one-fn-name :find-by-id-fn
    find-fn-name     :find-fn
    create-fn-name   :create-fn
    update-fn-name   :update-fn
    delete-fn-name   :delete-fn
    middleware       :middleware
    singular         :singular
    plural           :plural
    db-namespace     :database-ns
    :as              opts
    :or              {middleware []}}
   & other-routes]
  (let [plural           (or plural
                             (str singular "s"))
        url              (str "/api/" plural)
        find-fn-name     (or find-fn-name
                             (if db-namespace
                               (symbol db-namespace (str "find-" plural))
                               (symbol (str "find-" plural))))
        find-one-fn-name (or find-one-fn-name
                             (if db-namespace
                               (symbol db-namespace (str "find-" singular "-by-id"))
                               (symbol (str "find-" singular "-by-id"))))
        create-fn-name   (or create-fn-name
                             (if db-namespace
                               (symbol db-namespace (str "create-" singular "!"))
                               (symbol (str "create-" singular "!"))))
        update-fn-name   (or update-fn-name
                             (if db-namespace
                               (symbol db-namespace (str "update-" singular "!"))
                               (symbol (str "update-" singular "!"))))
        delete-fn-name   (or delete-fn-name
                             (if db-namespace
                               (symbol db-namespace (str "delete-" singular "!"))
                               (symbol (str "delete-" singular "!"))))]
    `(compojure.api.sweet/context ~url []
              :tags [~plural]
              ~@other-routes
              (compojure.api.sweet/GET "/" {:as ~'request}
                   :middleware ~middleware
                   (ring.util.http-response/ok (~find-fn-name (:db ~'request))))
              (compojure.api.sweet/GET "/:id" {:as ~'request}
                :path-params [~'id :- Long]
                :middleware ~middleware
                (if-let [~'res (~find-one-fn-name ~'id (:db ~'request))]
                  (ring.util.http-response/ok ~'res)
                  (ring.util.http-response/not-found)))
              (compojure.api.sweet/POST "/" {:as ~'request}
                :body [~'data s/Any]
                :middleware  ~middleware
                (ring.util.http-response/ok (~create-fn-name ~'data (:db ~'request) nil ~'request)))
              (compojure.api.sweet/PUT "/" {:as ~'request}
                :body [~'data s/Any]
                :middleware ~middleware
                (ring.util.http-response/ok (~update-fn-name ~'data (:db ~'request) nil ~'request)))
              (compojure.api.sweet/DELETE "/:id" {:as ~'request}
                :path-params [~'id :- s/Any]
                :middleware ~middleware
                (ring.util.http-response/ok (~delete-fn-name ~'id (:db ~'request)))))))


(comment

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
      (GET "/some-other-route" {:as request}
           :query-params [query :- s/Str]
           :middleware []
           (do-something query))
      ;;; you can add more routes here...
      )

    )



  )
