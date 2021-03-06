(ns table2qb.pipelines.cube-test
  (:require [clojure.test :refer :all]
            [table2qb.csv :refer [reader read-csv] :as csv]
            [table2qb.pipelines.cube :refer :all]
            [table2qb.pipelines.test-common :refer [default-config first-by example-csvw example-csv test-domain-data
                                                    maps-match? title->name eager-select]]
            [table2qb.util :as util]
            [table2qb.configuration.cube :as cube-config]
            [grafter.rdf.repository :as repo]
            [grafter.rdf :as rdf])
  (:import [java.io StringWriter]
           [java.net URI]))

(defmacro is-metadata-compatible [input-csv schema]
  `(testing "column sequence matches"
     (with-open [rdr# (reader ~input-csv)]
       (let [csv-columns# (-> rdr# (read-csv title->name) first keys ((partial map name)))
             meta-columns# (->> (get-in ~schema ["tableSchema" "columns"])
                                (remove #(get % "virtual" false))
                                (map #(get % "name")))]
         (is (= csv-columns# meta-columns#))))))

(deftest suppress-value-column-test
  (testing "value column"
    (is (= {"name" "value"
            "title" "Value"
            "suppressOutput" true}
           (suppress-value-column {"name"  "value"
                                   "title" "Value"} #{:value}))))

  (testing "non-value column"
    (let [col {"name" "flow"
               "title" "Flow"}]
      (is (= col (suppress-value-column col #{:value}))))))

(deftest observation-template-test
  (let [domain-data-prefix "http://example.com/data/"
        dataset-slug "test"
        dimension-names ["year" "flow" "code"]]
    (is (= "http://example.com/data/test/{+year}/{+flow}/{+code}" (observation-template domain-data-prefix dataset-slug dimension-names)))))

(deftest read-component-specifications-test
  (testing "returns a dataset of component-specifications"
    (let [cube-config (cube-config/get-cube-configuration (example-csv "regional-trade" "input.csv") default-config)
          component-specifications (read-component-specifications cube-config)]
      (testing "one row per component"
        (is (= 8 (count component-specifications))))
      (testing "geography component"
        (let [{:keys [component_attachment component_property]}
              (first-by :component_slug "geography" component-specifications)]
          (is (= component_attachment "qb:dimension"))
          (is (= component_property "http://purl.org/linked-data/sdmx/2009/dimension#refArea"))))
      (testing "compare with component-specifications.csv"
        (testing "parsed contents match"
          (let [expected-records (csv/read-all-csv-records (example-csvw "regional-trade" "component-specifications.csv"))]
            (is (= (set expected-records)
                   (set component-specifications)))))
        (testing "serialised contents match"
          (let [string-writer (StringWriter.)]
            (csv/write-csv string-writer (sort-by :component_slug component-specifications))
            (is (= (slurp (example-csvw "regional-trade" "component-specifications.csv"))
                   (str string-writer))))))
      (testing "compare with component-specifications.json"
        (testing "parsed contents match"
          (maps-match? (util/read-json (example-csvw "regional-trade" "component-specifications.json"))
                       (component-specification-metadata
                         "regional-trade.slugged.normalised.csv"
                         test-domain-data
                         "Regional Trade Component Specifications"
                         "regional-trade"))))))
  (testing "name is optional"
    (let [metadata (component-specification-metadata "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "dc:title"))))))
    
(deftest dataset-test
  (testing "compare with dataset.json"
    (maps-match? (util/read-json (example-csvw "regional-trade" "dataset.json"))
                 (dataset-metadata
                   "regional-trade.slugged.normalised.csv"
                   test-domain-data
                   "Regional Trade"
                   "regional-trade")))
 (testing "name is optional"
   (let [metadata (dataset-metadata "components.csv" test-domain-data "" "ds-slug")]
     (is (= nil (get metadata "rdfs:label"))))))

(deftest data-structure-definition-test
  (testing "compare with data-structure-definition.json"
    (maps-match? (util/read-json (example-csvw "regional-trade" "data-structure-definition.json"))
                 (data-structure-definition-metadata
                   "regional-trade.slugged.normalised.csv"
                   test-domain-data
                   "Regional Trade"
                   "regional-trade")))
  (testing "name is optional"
    (let [metadata (data-structure-definition-metadata "components.csv" test-domain-data "" "ds-slug")]
      (is (= nil (get metadata "rdfs:label"))))))

(defn get-observations
  [observations-source column-config]
  (let [cube-config (cube-config/get-cube-configuration observations-source column-config)]
    (with-open [r (csv/reader observations-source)]
      (doall (cube-config/observation-records r cube-config)))))

(deftest observations-test
  (testing "sequence of observations"
    (testing "regional trade example"
      (let [observations (get-observations (example-csv "regional-trade" "input.csv") default-config)
            observation (first observations)]
        (testing "one observation per row"
          (is (= 44 (count observations))))
        (testing "one column per component"
          (is (= 7 (count observation))))
        (testing "slugged columns"
          (are [expected actual] (= expected actual)
               "gbp-total" (:measure_type observation)
            "gbp-million" (:unit observation)
            "0-food-and-live-animals" (:sitc_section observation)
            "export" (:flow observation)))))
    (testing "overseas trade example"
      (let [observations (doall (get-observations (example-csv "overseas-trade" "ots-cn-sample.csv") default-config))
            observation (first observations)]
        (testing "one observation per row"
          (is (= 20 (count observations))))
        (testing "one column per component"
          (is (= 7 (count observation))))
        (testing "slugged columns"
          (are [expected actual] (= expected actual)
               "gbp-total" (:measure_type observation)
            "gbp-million" (:unit observation)
            "cn#cn8_28399000" (:combined_nomenclature observation)
            "export" (:flow observation))))))
  (testing "observation metadata"
    (testing "regional trade example"
      (let [obs-source (example-csv "regional-trade" "input.csv")
            cube-config (cube-config/get-cube-configuration obs-source default-config)
            obs-meta (observations-metadata "observations.csv"
                                            test-domain-data
                                            "regional-trade"
                                            cube-config)]
        (maps-match? (util/read-json (example-csvw "regional-trade" "observations.json"))
                     obs-meta)))
    (testing "overseas trade example"
      (let [obs-source (example-csv "overseas-trade" "ots-cn-sample.csv")
            cube-config (cube-config/get-cube-configuration obs-source default-config)
            obs-meta (observations-metadata "ignore-me.csv" test-domain-data "overseas-trade" cube-config)]
        (is-metadata-compatible (example-csv "overseas-trade" "ots-cn-sample.csv")
                                obs-meta)))))

(deftest used-codes-test
  (testing "codelists metadata"
    (maps-match? (util/read-json (example-csvw "regional-trade" "used-codes-codelists.json"))
                 (used-codes-codelists-metadata "regional-trade.slugged.normalised.csv"
                                                test-domain-data
                                                "regional-trade")))
  (testing "codes metadata"
    (let [obs-source (example-csv "regional-trade" "input.csv")
          cube-config (cube-config/get-cube-configuration obs-source default-config)
          expected-json (util/read-json (example-csvw "regional-trade" "used-codes-codes.json"))]
      (maps-match? expected-json
                   (used-codes-codes-metadata "regional-trade.slugged.csv"
                                              test-domain-data
                                              "regional-trade"
                                              cube-config)))))

(deftest cube-pipeline-test
  (let [dataset-name "Regional Trade"
        dataset-slug "regional-trade"
        base-uri (URI. "http://example.com/")
        dataset-uri "http://example.com/data/regional-trade"
        dsd-uri (str dataset-uri "/structure")
        repo (repo/sail-repo)]
    (rdf/add repo (cube-pipeline (example-csv "regional-trade" "input.csv")
                                 dataset-name
                                 dataset-slug
                                 default-config
                                 base-uri))

    (testing "Dataset title and label"
      (let [q (str "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                   "PREFIX dc: <http://purl.org/dc/terms/>"
                   "SELECT ?title ?label WHERE {"
                   "  <" dataset-uri "> dc:title ?title ;"
                   "                    rdfs:label ?label ."
                   "}")
            {:keys [title label] :as binding} (first (eager-select repo q))]
        (is (= dataset-name (str title)))
        (is (= dataset-name (str label)))))

    (testing "DSD title and label"
      (let [dsd-label (derive-dsd-label dataset-name)
            q (str "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
                   "PREFIX dc: <http://purl.org/dc/terms/>"
                   "SELECT ?title ?label WHERE {"
                   "  <" dsd-uri "> dc:title ?title ;"
                   "                    rdfs:label ?label ."
                   "}")
            {:keys [title label] :as binding} (first (eager-select repo q))]
        (is (= dsd-label (str title)))
        (is (= dsd-label (str label)))))

    (testing "DSD type"
      (let [q (str "PREFIX qb: <http://purl.org/linked-data/cube#>"
                   "ASK WHERE {"
                   "  <" dsd-uri "> a qb:DataStructureDefinition ."
                   "}")]
        (is (repo/query repo q))))))

;; TODO: Need to label components and their used-code codelists if dataset-name is not blank
