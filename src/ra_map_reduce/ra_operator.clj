(ns ra-map-reduce.ra-operator
  (:require [clojure.set]))

(defn reduce-identity
  "Default reduce function. Takes key and vals and returns them."
  [key vals]
  [key vals])

(defn select
  "Semantic equivalent to RA select. Takes function and evaluates it with given
   record. passed and failed are used as group keys."
  [cond-func]
  {:map (fn [record]
          (if (cond-func record)
            ["passed" record]
            ["failed" [(keyword (record :table))
                       (record :offset)]]))
   :reduce reduce-identity})

(defn project
  "Takes a set of attribute keys and selects a subset of the record
   map by those attribute keys. Groups by table by default."
  [attr-keys gk-key-func]
  {:map (fn [record]
          [(gk-key-func record)
           (select-keys record attr-keys)])
   :reduce reduce-identity})

(defn agg-group
  "Takes a set of group keys and aggregation function map and
   performs aggregation and grouping. If no group keys are specified,
   then grouping is done on table. Aggregation function map must be keyed
   by attribute name and values should be functions that expect a list of values."
  [group-keys agg-func-map]
  {:map (fn [record]
          [(if (some? group-keys)
             (mapv record (sort group-keys))
             (record :table)) record])
   :reduce (fn [key vals]
             [key (into (array-map) (map (fn [[k af]]
                                           [k (af (map k vals))]) agg-func-map))])})

(defn relate-all
  "Takes two relations and performs cartesian product. The
   map records are not combined and represented as [r1 r2] instead."
  [rel1 rel2]
  (let [reps (map (fn [x]
                    (repeat (count rel2) x)) rel1)
        relate (map (fn [r]
                      [r rel2]) reps)
        final  (map (fn [[rep v]]
                      (map (fn [r1 r2]
                             [r1 r2]) rep v)) relate)]
    (reduce concat final)))

(defn combine-record
  "Takes two record maps and combines them into one map. Alike key
   names are renamed such that k becomes k.1 k.2 with both having the
   value of k. Distinct key names are preserved."
  [[m1 m2]]
  (let [[k1 k2] [(set (keys m1)) (set (keys m2))]
        int-kv (map (fn [k]
                      [k (m1 k)]) (clojure.set/intersection k1 k2))
        int-keys (set (map first int-kv))
        no-change (clojure.set/difference (clojure.set/union k1 k2) int-keys)
        no-change-kv (map (fn [k]
                            [k ((if (contains? m1 k)
                                  m1
                                  m2) k)]) no-change)
        ren-keys (map (fn [k]
                        [k
                         (keyword (str (name k) ".1"))
                         (keyword (str (name k) ".2"))]) int-keys)
        change-k1v (map (fn [[ok k1 _]]
                          [k1 (m1 ok)]) ren-keys)
        change-k2v (map (fn [[ok _ k2]]
                          [k2 (m2 ok)]) ren-keys)
        kvs (concat no-change-kv change-k1v change-k2v)
        ks (map first kvs)
        vs (map second kvs)]
    (zipmap ks vs)))

(defn cartesian-product
  "Takes a set of group-keys and creates a vector group key with
   values from the record. Uses default_key if no key is specified."
  [group-keys reduce-gk]
  {:map (fn [record]
          [(if (some? group-keys)
             (mapv record (sort group-keys))
             "default_key") record])
   :reduce (fn [key vals]
             (let [groups (group-by (keyword reduce-gk) vals)
                   [_ first-table] (first groups)
                   [_ second-table] (second groups)]
               [key (map (fn [r]
                    (combine-record r)) (relate-all first-table second-table))]))})

(defn set-operation
  "Takes some set operation, stream s1, and stream s2 and returns
   the op on the two streams and returns a vector."
  [op s1 s2]
  (vec (op (set s1) (set s2))))

(defn mr-union
  "Takes two streams s1 and s1 and performs map reduce union."
  [s1 s2]
  (set-operation clojure.set/union s1 s2))

(defn mr-difference
  "Takes two streams s1 and s1 and performs map reduce difference."
  [s1 s2]
  (set-operation clojure.set/difference s1 s2))

(defn mr-intersection
  "Takes two streams s1 and s1 and performs map reduce intersection."
  [s1 s2]
  (set-operation clojure.set/intersection s1 s2))


