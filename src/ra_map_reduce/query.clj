(ns ra-map-reduce.query
  (:require [ra-map-reduce.ra-operator :as ra])
  (:require [ra-map-reduce.model :as model]))

(defn select [data filt-cond-func]
  "Takes data and a function to perform filtering and returns
   a relation filtered according to the filter condition function."
  (let [{mp :map rd :reduce} (ra/select (fn [r]
                                       (filt-cond-func r)))]
    (model/map-reduce data mp rd)))

(defn project [data attr-set]
  "Uses generic project to select name, city, stadium from each record."
  (let [{mp :map rd :reduce} (ra/project attr-set)]
    (model/map-reduce data mp rd)))

(defn join [sep-key join-cond-func rel1 rel2]
  "Takes a key to separate the relations in the reduce stage of cartesian
   product and a function to perform the join condition."
  (let [{cp-mp :map cp-rd :reduce} (ra/cartesian-product nil sep-key)
        {s-mp :map s-rd :reduce} (ra/select (fn [r]
                                             (join-cond-func r)))]
    (model/map-reduce (model/grab-records
     (model/map-reduce [rel1 rel2] cp-mp cp-rd)) s-mp s-rd)))

(defn agg-group [group-keys key-func-map data]
  "Takes grouping keys, function map by key, and a relation and returns
   aggregation with grouping on relation according to grouping keys and
   function map."
  (let [{mp :map rd :reduce} (ra/agg-group group-keys key-func-map)]
    (model/map-reduce data mp rd)))


