(defproject sweet-crud "0.1.14"
  :description "Two complementary macros to create compojure.api.sweet CRUD routes, with (configurable) database calls."
  :url "https://github.com/Kah0ona/sweet-crud.git"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [compojure "1.6.1"]
                 [metosin/compojure-api "2.0.0-alpha31"]
                 [honeysql "0.9.10"]]
  :jar-name "sweet-crud.jar"
  :repl-options {:init-ns sweet-crud.core})
