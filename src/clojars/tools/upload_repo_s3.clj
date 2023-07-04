(ns clojars.tools.upload-repo-s3
  (:gen-class)
  (:require
   [clojars.config :as config]
   [clojars.event :as event]
   [clojars.file-utils :as fu]
   [clojars.s3 :as s3]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as component]))

(defn print-status
  [{:keys [changed new skipped]}]
  (printf "Uploaded: %s (new: %s, changed: %s); Skipped %s\n"
          (+ changed new)
          new
          changed
          skipped)
  (flush))

(defn maybe-upload-file
  [s3-client existing repo stats f]
  (let [path (fu/subpath
              (.getAbsolutePath repo)
              (.getAbsolutePath f))]
    ;; ETag from s3 is an md5 sum, but only for non-multipart
    ;; uploads. Luckily, we don't upload artifacts as multipart
    (when (= 0 (rem (apply + (vals stats)) 1000))
      (print-status stats))
    (if (= (existing path) (fu/checksum f :md5))
      (update stats :skipped inc)
      (let [reason (if (existing path) :changed :new)]
        (printf "=> Uploading (%s): %s\n" (name reason) path)
        (s3/put-file s3-client path f {:ACL "public-read"})
        (update stats reason inc)))))

(defn get-existing [s3-client subpath]
  (printf "Retrieving current artifact list [subpath: %s] (this may take a while)\n" subpath)
  (flush)
  (into {}
        (map (juxt :Key :ETag))
        (s3/list-objects s3-client subpath)))

(defn local-files [repo subpath]
  (let [local-dir (if subpath (io/file repo subpath) (io/file repo))]
    (filter (memfn isFile) (file-seq local-dir))))

(defn upload-repo [s3-client repo subpath]
  (let [existing (get-existing s3-client subpath)
        local-files (local-files repo subpath)]
    (println "Starting upload")
    (flush)
    (let [stats (reduce
                 (partial maybe-upload-file s3-client existing repo)
                 {:skipped 0
                  :changed 0
                  :new 0}
                 local-files)]
      (print-status stats))))

(defn -main [& args]
  (if (< (count args) 2)
    (println "Usage: repo-path bucket-name [subpath & opts]")
    (let [[repo bucket subpath & opts] args
          opts-map (zipmap (map read-string opts)
                           (repeat true))]
      (upload-repo (s3/s3-client bucket)
                   (io/file repo)
                   subpath)
      (when (:gen-index opts-map)
        (let [event-emitter (component/start (event/new-sqs-emitter
                                              (:event-queue (config/config))))]
          (event/emit event-emitter :repo-path-needs-index {:path subpath}))))))
