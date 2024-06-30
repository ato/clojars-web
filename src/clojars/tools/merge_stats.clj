(ns clojars.tools.merge-stats
  (:gen-class)
  (:import
   (java.io
    FileReader
    PushbackReader)))

(set! *warn-on-reflection* true)

(defn -main [& args]
  (let [stats (map
               (fn [^String filename]
                 (try
                   (read (PushbackReader. (FileReader. filename)))
                   (catch Exception e
                     (binding [*out* *err*]
                       (println (format "Failed to read %s: %s" filename (.getMessage e))))
                     {})))
               args)]
    (prn (apply merge-with (partial merge-with +) stats))))
