(ns clojars.routes.repo-listing
  (:require
   [clojars.web.repo-listing :as repo-listing]
   [compojure.core :as compojure :refer [GET HEAD]]))

(defn routes
  [repo-lister]
  (compojure/routes
   (GET ["/list-repo"]
        {{:keys [path]} :params}
        (repo-listing/index-for-path repo-lister path))
   (HEAD ["/list-repo"]
         {{:keys [path]} :params}
         (repo-listing/index-for-path repo-lister path))))