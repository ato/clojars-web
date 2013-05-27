(ns clojars.web.dashboard
  (:require [clojars.web.common :refer [html-doc jar-link group-link tag]]
            [clojars.db :refer [jars-by-username find-groupnames recent-jars]]
            [hiccup.element :refer [unordered-list link-to]]))

(defn index-page [account]
  (html-doc account nil
    [:p "Clojars is a " [:strong "dead easy"] " community repository for"
     " open source " (link-to "http://clojure.org/" "Clojure")
     " libraries. "]
    [:div {:class "useit"}
     [:div {:class "lein"}
      [:h3 "pushing with leiningen"]
      [:pre
       (tag "$") " lein pom\n"
       (tag "$") " scp pom.xml mylib.jar clojars@clojars.org:"]]

     "It's the " [:strong "default repository"] " for "
     (link-to "http://leiningen.org" "Leiningen")
     ", but you can use it with other build tools like "
     (link-to "http://maven.apache.org/" "Maven") " as well."
     [:div {:class "maven"}
      [:h3 "maven repository"]
      [:pre
       (tag "<repository>\n")
       (tag "  <id>") "clojars.org" (tag "</id>\n")
       (tag "  <url>") "http://clojars.org/repo" (tag "</url>\n")
       (tag "</repository>")]]]

    [:p "To " [:strong "get started"] " pushing your own projects "
     (link-to "/register" "create an account")
     " and then check out the "
     (link-to "http://wiki.github.com/ato/clojars-web/tutorial"
              "tutorial") ". Alternatively, "
     (link-to "/projects" "browse") " the repository."]
    [:h2 "Recently pushed projects"]
    (unordered-list (map jar-link (recent-jars)))))

(defn dashboard [account]
  (html-doc account "Dashboard"
    [:h1 (str "Dashboard (" account ")")]
    [:h2 "Your projects"]
    (unordered-list (map jar-link (jars-by-username account)))
    (link-to "http://wiki.github.com/ato/clojars-web/pushing" "add new project")
    [:h2 "Your groups"]
    (unordered-list (map group-link (find-groupnames account)))))
