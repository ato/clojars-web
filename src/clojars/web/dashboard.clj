(ns clojars.web.dashboard
  (:require [clojars.web.common :refer [html-doc html-doc-with-large-header jar-link group-link tag]]
            [clojars.web.structured-data :as structured-data]
            [clojars.db :refer [jars-by-username find-group-ids recent-jars]]
            [clojars.stats :as stats]
            [hiccup.element :refer [unordered-list link-to]]
            [clojars.web.helpers :as helpers]))

(defn recent-jar [stats jar-map]
  (let [description (:description jar-map)
        truncate-length 120]
    [:li.col-xs-12.col-sm-6.col-md-4
     [:div.recent-jar
      [:h3.recent-jar-title
       (jar-link jar-map)]
      [:p.recent-jar-description
       (if (> (count description) truncate-length)
         (str (subs description 0 truncate-length) "...")
         description)]
      [:p.hint.total-downloads "Downloads: " (-> (stats/download-count stats
                                                                       (:group_name jar-map)
                                                                       (:jar_name jar-map))
                                                 (stats/format-stats))]]]))


(defn index-page [db stats account]
  (html-doc-with-large-header nil {:account account
                                   :description "Clojars is a dead easy community repository for open source Clojure libraries."}
    structured-data/website
    structured-data/organisation
    [:article.row
     (helpers/select-text-script)
     [:div.push-information.col-xs-12.col-sm-4
      [:h3.push-header "Push with "
       (link-to "http://leiningen.org/" "Leiningen")]
      [:div#leiningen.push-example {:onClick "selectText('leiningen');"}
       [:pre.push-example-leiningen
        (tag "$") " lein deploy clojars\n"]]]
     [:div.push-information.col-xs-12.col-sm-4
      [:h3.push-header "Push with "
        (link-to "http://boot-clj.com/" "Boot")
        " (using "
        (link-to "https://github.com/adzerk/bootlaces" "bootlaces")
        ")"]
      [:div#boot.push-example {:onClick "selectText('boot');"}
       [:pre.push-example-boot
        (tag "$") " boot build-jar push-snapshot\n"
        (tag "$") " boot build-jar push-release\n"]]]
     [:div.push-information.col-xs-12.col-sm-4
      [:h3.push-header "Maven Repository"]
      [:div#maven.push-example {:onClick "selectText('maven');"}
       [:pre
        (tag "<repository>\n")
        (tag "  <id>") "clojars.org" (tag "</id>\n")
        (tag "  <url>") "https://repo.clojars.org" (tag "</url>\n")
        (tag "</repository>")]]]]
    [:div.recent-jars-header-container.row
     [:h2.recent-jars-header.col-xs-12
      "Recently pushed projects"]]
    [:ul.recent-jars-list.row (map #(recent-jar stats %) (recent-jars db))]))

(defn dashboard [db account]
  (html-doc "Dashboard" {:account account}
    [:div.light-article.col-xs-12
     [:h1 (str "Dashboard (" account ")")]
     [:div.col-xs-12.col-sm-4
      [:div.dash-palette
       [:h2 "Your Projects"]
       (let [jars (jars-by-username db account)]
         (if (seq jars)
           (unordered-list (map jar-link jars))
           [:p "You don't have any projects, would you like to "
            (link-to "http://wiki.github.com/clojars/clojars-web/pushing" "add one")
            "?"]))]]
     [:div.col-xs-12.col-sm-4
      [:div.dash-palette
       [:h2 "Your Groups"]
       (unordered-list (map group-link (find-group-ids db account)))]]
     [:div.col-xs-12.col-sm-4
      [:div.dash-palette
       [:h2 "FAQ"]
       [:ul
        [:li (link-to "https://github.com/clojars/clojars-web/wiki/Tutorial" "How I create a new project?")]
        [:li (link-to "http://wiki.github.com/clojars/clojars-web/pushing" "How do I deploy to clojars?")]
        [:li (link-to "https://github.com/clojars/clojars-web/wiki/Data" "How can I access clojars data programatically?")]
        [:li (link-to "https://github.com/clojars/clojars-web/wiki/Groups" "What are groups?")]
        [:li (link-to "https://github.com/clojars/clojars-web/wiki/POM" "What does my POM need to look like?")]]]]]))
