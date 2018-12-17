(ns table2qb.util
  (:require [csv2rdf.source :as source]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as string])
  (:import [java.io File]))

(defn exception? [x] (instance? Exception x))

(defn map-values
  "Transforms each value within a map with the given transform function. Returns a new map."
  [m f]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn csv-file->metadata-uri [^File csv-file]
  (let [csv-dir (.getParentFile csv-file)
        meta-file (io/file csv-dir "meta.json")]
    (.toURI meta-file)))

(defn create-metadata-source [csv-file-str metadata-json]
  (let [meta-uri (csv-file->metadata-uri (io/file csv-file-str))]
    (source/->MapMetadataSource meta-uri metadata-json)))

(defn tempfile [filename extension]
  (File/createTempFile filename extension))

(defn read-json
  "Reads a JSON document from an io/IOFactory source"
  [json-source]
  (with-open [r (io/reader json-source)]
    (json/read r)))

(defn blank->nil
  "Returns the input value if it is not blank, otherwise nil."
  [value]
  (if-not (string/blank? value)
    value))
