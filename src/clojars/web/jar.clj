(ns clojars.web.jar
  (:require [clojars.web.common :refer [html-doc jar-link group-link
                                        tag jar-url jar-name group-is-name? user-link
                                        jar-fork? single-fork-notice
                                        simple-date]]
            hiccup.core
            [hiccup.element :refer [link-to image]]
            [hiccup.form :refer [submit-button]]
            [clojars.web.safe-hiccup :refer [form-to raw]]
            [clojars.db :refer [find-jar jar-exists]]
            [clojars.stats :as stats]
            [clojars.config :refer [config]]
            [clojars.file-utils :as fu]
            [ring.util.codec :refer [url-encode]]
            [cheshire.core :as json]
            [clojars.web.helpers :as helpers]
            [clojars.web.structured-data :as structured-data]
            [clojars.db :as db]
            [clojure.set :as set]))

(defn url-for [jar]
  (str (jar-url jar) "/versions/" (:version jar)))

(defn repo-url [jar]
  (str (:cdn-url @config) "/" (-> jar :group_name fu/group->path) "/" (:jar_name jar) "/"))

(defn maven-jar-url [jar]
 (str "http://search.maven.org/#"
   (url-encode (apply format "artifactdetails|%s|%s|%s|jar"
        ((juxt :group_name :jar_name :version) jar)))))

(let [github-re #"^https?://github.com/([^/]+/[^/]+)"]

  (defn github-info [jar]
    (let [url (get-in jar [:scm :url])
          user-repo (->> (str url) (re-find github-re) second)]
      user-repo))

  (defn commit-url [jar]
    (let [{:keys [url tag]} (:scm jar)
          base-url (first (re-find github-re (str url)))]
      (when (and base-url tag) (str base-url "/commit/" tag)))))

(defn dependency-link [db dep]
  (link-to
    (if (jar-exists db (:group_name dep) (:jar_name dep)) (jar-url dep) (maven-jar-url dep))
    (str (jar-name dep) " " (:version dep))))

(defn version-badge-url [jar]
  (format "https://img.shields.io/clojars/v%s.svg" (jar-url jar)))

(defn badge-markdown [jar]
  (format
    "[![Clojars Project](%s)](https://clojars.org%s)"
    (version-badge-url jar)
    (jar-url jar)))

(defn dependency-section [db title id dependencies]
  (if (empty? dependencies) '()
    (list
      [:h3 title]
      [(keyword (str "ul#" id))
       (for [dep dependencies]
         [:li (dependency-link db dep)])])))

; handles link-to throwing an exception when given a non-url
(defn safe-link-to [url text]
  (try (link-to url text)
    (catch Exception e text)))

(defn fork-notice [jar]
  (when (jar-fork? jar)
    single-fork-notice))

(defn show-jar [db reporter stats account jar recent-versions count]
  (let [total-downloads (-> (stats/download-count stats
                                                  (:group_name jar)
                                                  (:jar_name jar))
                            (stats/format-stats))
        downloads-this-version (-> (stats/download-count stats
                                                         (:group_name jar)
                                                         (:jar_name jar)
                                                         (:version jar))
                                   (stats/format-stats))
        title (format "[%s/%s \"%s\"]" (:group_name jar) (:jar_name jar) (:version jar))]
    (html-doc
      title {:account account :description (format "%s %s" title (:description jar))
                                                :label1  (str "Total downloads / this version")
                                                :data1   (format "%s / %s" total-downloads downloads-this-version)
                                                :label2  "Coordinates"
                                                :data2   (format "[%s \"%s\"]" (jar-name jar) (:version jar))}
      [:div.light-article.row
       ;; TODO: this could be made more semantic by attaching the metadata to #jar-title, but we're waiting on https://github.com/clojars/clojars-web/issues/482
       (structured-data/breadcrumbs (if (group-is-name? jar)
                                      [{:url  (str "https://clojars.org/" (jar-name jar))
                                        :name (:jar_name jar)}]
                                      [{:url  (str "https://clojars.org/groups/" (:group_name jar))
                                        :name (:group_name jar)}
                                       {:url  (str "https://clojars.org/" (jar-name jar)) ;; TODO: Not sure if this is a dirty hack or a stroke of brilliance
                                        :name (:jar_name jar)}]))

       (helpers/select-text-script)
       [:div#jar-title.col-xs-12.col-sm-9
        [:h1 (jar-link jar)]
        [:p.description (:description jar)]
        [:ul#jar-info-bar.row
         [:li.col-xs-12.col-sm-4
          (if-let [gh-info (github-info jar)]
            (link-to (format "https://github.com/%s" gh-info)
                     (helpers/retinized-image "/images/github-mark.png" "GitHub")
                     gh-info)
            [:p.github
             (helpers/retinized-image "/images/github-mark.png" "GitHub")
             "N/A"])]
         [:li.col-xs-12.col-sm-4
          total-downloads
          " Downloads"]
         [:li.col-xs-12.col-sm-4
          downloads-this-version
          " This Version"]]
        [:h2 "Leiningen/Boot"]
        [:div#leiningen-coordinates.package-config-example
         {:onClick "selectText('leiningen-coordinates');"}
         [:pre
          (tag "[")
          (jar-name jar)
          [:span.string " \""
           (:version jar) "\""] (tag "]")]]

        [:h2 "Clojure CLI"]
        [:div#deps-coordinates.package-config-example
         {:onClick "selectText('deps-coordinates');"}
         [:pre
          (jar-name jar)
          \space
          (tag "{")
          ":mvn/version "
          [:span.string \" (:version jar) \"]
          (tag "}")]]

        [:h2 "Gradle"]
        [:div#gradle-coordinates.package-config-example
         {:onClick "selectText('gradle-coordinates');"}
         [:pre
          "compile "
          [:span.string
           \'
           (:group_name jar)
           ":"
           (:jar_name jar)
           ":"
           (:version jar)
           \']]]

        [:h2 "Maven"]
        [:div#maven-coordinates.package-config-example
         {:onClick "selectText('maven-coordinates');"}
         [:pre
          (tag "<dependency>\n")
          (tag "  <groupId>") (:group_name jar) (tag "</groupId>\n")
          (tag "  <artifactId>") (:jar_name jar) (tag "</artifactId>\n")
          (tag "  <version>") (:version jar) (tag "</version>\n")
          (tag "</dependency>")]]
        (list
          (fork-notice jar))]
       [:ul#jar-sidebar.col-xs-12.col-sm-3
        [:li
         [:h4 "Pushed by"]
         (user-link (:user jar)) " on "
         [:span {:title (str (java.util.Date. (:created jar)))} (simple-date (:created jar))]
         (if-let [url (commit-url jar)]
           [:span.commit-url " with " (link-to url "this commit")])]
        [:li
         [:h4 "Recent Versions"]
         [:ul#versions
          (for [v recent-versions]
            [:li (link-to (url-for (assoc jar
                                     :version (:version v)))
                          (:version v))])]
         ;; by default, 5 versions are shown. If there are only 5 to
         ;; see, then there's no reason to show the 'all versions' link
         (when (> count 5)
           [:p (link-to (str (jar-url jar) "/versions")
                        (str "Show All Versions (" count " total)"))])]
        (let [dependencies
              (dependency-section db "Dependencies" "dependencies"
                                  (remove #(not= (:scope %) "compile")
                                          (map
                                            #(set/rename-keys % {:dep_group_name :group_name
                                                                 :dep_jar_name   :jar_name
                                                                 :dep_version    :version
                                                                 :dep_scope      :scope})
                                            (db/find-dependencies db
                                                                  (:group_name jar)
                                                                  (:jar_name jar)
                                                                  (:version jar)))))]
          (when-not (empty? dependencies)
            [:li dependencies]))
        (when-let [homepage (:homepage jar)]
          [:li.homepage
           [:h4 "Homepage"]
           (safe-link-to homepage homepage)])
        (when-let [licenses (seq (:licenses jar))]
          [:li.license
           [:h4 "License"]
           [:ul#licenses
            (for [{:keys [name url]} licenses]
              [:li (safe-link-to url name)])]])
        [:li
         [:h4 "Version Badge"]
         [:p
          "Want to display the "
          (link-to (version-badge-url jar) "latest version")
          " of your project on Github? Use the markdown code below!"]
         [:textarea#version-badge
          {:readonly "readonly" :rows 6 :onClick "selectText('version-badge')"}
          (badge-markdown jar)]]]])))

(defn repo-note [jar]
  [:div
   [:h2 "Maven Repository"]
   [:p
    "If you are looking for URLs to jar files or "
    (link-to "https://github.com/clojars/clojars-web/wiki/Stable-SNAPSHOT-Identifiers" "stable identifiers")
    " for SNAPSHOT versions you can take a look at "
    (link-to  (repo-url jar) (str "the full Maven repository for " (jar-name jar) "."))]])

(defn show-versions [account jar versions]
  (html-doc (str "all versions of "(jar-name jar)) {:account account}
            [:div.light-article
             [:h1 "all versions of "(jar-link jar)]
             [:div.versions
              [:ul
               (for [v versions]
                 [:li.col-xs-12.col-sm-6.col-md-4.col-lg-3
                  (link-to (url-for (assoc jar :version (:version v)))
                           (:version v))])]]]
            [:div.light-article
             (repo-note jar)]))

(let [border-color "#e2e4e3"
      bg-color "#fff"
      artifact-color  "#4098cf"
      version-color "#87cf29"
      bracket-color "#ffb338"
      ampersand-color "#888"
      clojars-color "#ffb338"]
  (defn svg-template [jar-id version]
    (let [width-px (+ 138 (* (+ (count jar-id) (count version)) 6))]
      [:svg {:width (str (* width-px 0.044) "cm")
             :height "0.90cm"
             :viewBox (str "0 0 " width-px " 20")
             :xmlns "http://www.w3.org/2000/svg"
             :version "1.1"}
       [:rect {:x 0,
               :y 0,
               :width width-px,
               :height 20,
               :rx 3,
               :fill border-color}]
       [:rect {:x 2,
               :y 2,
               :width (- width-px 4),
               :height 16,
               :rx 3,
               :fill bg-color}]
       [:text {:x 7,
               :y 13,
               :font-family "monospace",
               :font-size 10,
               :fill "#dddddd"}
        [:tspan {:fill bracket-color} "["]
        [:tspan {:fill artifact-color} jar-id]
        [:tspan " "]
        [:tspan {:fill version-color} (str \" version \")]
        [:tspan {:fill bracket-color} "]"]]
       [:text {:x (- width-px 55),
               :y 14,
               :font-family "Verdana",
               :font-size 8,
               :fill ampersand-color}
        [:tspan "@"]
        [:tspan {:fill clojars-color} "clojars.org"]]])))

(defn make-latest-version-svg [db group-id artifact-id]
  (let [jar (find-jar db group-id artifact-id)]
    (hiccup.core/html
     "<?xml version=\"1.0\" standalone=\"no\"?>"
     "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\"
 \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">"
     (svg-template (jar-name jar) (:version jar)))))

(defn make-latest-version-json [db group-id artifact-id]
  "Return the latest version of a JAR as JSON"
  (let [jar (find-jar db group-id artifact-id)]
    (json/generate-string (select-keys jar [:version]))))
