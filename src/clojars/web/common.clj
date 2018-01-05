(ns clojars.web.common
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js]]
            [hiccup.element :refer [link-to unordered-list image]]
            [clojars.web.helpers :as helpers]
            [clojars.web.safe-hiccup :refer [html5 raw form-to]]
            [clojars.web.structured-data :as structured-data]
            [clojure.string :refer [join]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.util.codec :refer [url-encode]]
            [clojars.maven :as maven]
            [clojure.edn :as edn]))

(defn when-ie [& contents]
  (str
   "<!--[if lt IE 9]>"
   (html contents)
   "<![endif]-->"))

(defn footer
  "We normally want to include links in the footer, except on pages which contain sensitive URL's like password reset.
  This is so users don't accidentally leak the password reset URL in the referer header."
  [footer-links?]
  (if footer-links?
    [:footer.row
     (link-to "https://github.com/clojars/clojars-web/wiki/About" "about")
     (link-to "http://status.clojars.org" "status")
     (link-to "/projects" "projects")
     (link-to "https://github.com/clojars/clojars-web/wiki/Contributing" "contribute")
     (link-to "https://groups.google.com/forum/?fromgroups#!topicsearchin/clojars-maintainers/group:clojars-maintainers$20AND$20subject:ann" "news")
     (link-to "https://github.com/clojars/clojars-web/wiki/Contact" "contact")
     (link-to "https://github.com/clojars/clojars-web" "code")
     (link-to "/security" "security")
     (link-to "/dmca" "DMCA")
     (link-to "https://github.com/clojars/clojars-web/wiki/" "help")
     [:div.sponsors
      [:div.row
       "sponsored by"
       (link-to "https://www.bountysource.com/teams/clojars/backers" "individual contributors")
       "with in-kind sponsorship from:"]
      [:div.row
       [:table
        [:tr
         [:td.sponsor
          (link-to "https://dnsimple.link/resolving-clojars"
                   [:span "resolving with" [:br]]
                   [:span
                    (image "https://cdn.dnsimple.com/assets/resolving-with-us/logo-light.png" "DNSimple")])]
         [:td.sponsor
          (link-to "http://fastly.com/"
                   (image "/images/fastly-logo.png" "Fastly"))]
         [:td.sponsor
          (link-to "https://pingometer.com/"
                   (image "/images/pingometer-logo.svg" "Pingometer"))]]]]
      [:div.row
       [:table
        [:tr
         [:td.sponsor
          (link-to "https://www.rackspace.com"
                   (image "/images/rackspace-logo.svg" "Rackspace"))]
         [:td.sponsor
          (link-to "http://sentry.io/"
                   (image "/images/sentry-logo.png" "Sentry"))]
         [:td.sponsor
          (link-to "https://www.statuspage.io"
                   (image "/images/statuspage-io-logo.svg" "StatusPage.io"))]]]]]
     [:div.row.sponsors
      "remixed by"
      (link-to "http://www.bendyworks.com/"
               [:img {:src "/images/bendyworks-logo.svg" :alt "Bendyworks Inc." :width "150"}])
      "member project of"
      (link-to "https://sfconservancy.org/"
               [:img {:src "/images/sfconservancy-logo.svg" :alt "Software Freedom Conservancy" :height "40"}])]
     ]
    [:footer.row]))

(defn google-analytics-js []
  [:script (raw "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-51806851-1', 'clojars.org');
  ga('send', 'pageview');")])

(defn typekit-js []
  [:script "try{Typekit.load({async:true});}catch(e){}"])

(defn html-doc [title ctx & body]
  (html5 {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]
    [:link {:type "application/opensearchdescription+xml"
            :title "Clojars"
            :href "/opensearch.xml"
            :rel "search"}]
    [:link {:type "image/png"
            :href "/favicon.png"
            :rel "icon"}]
    (structured-data/meta-tags (assoc ctx :title (if title
                                                   title
                                                   "Clojars"))) ;; TODO: talk about whether we should refactor signature of html-doc
    [:title
     (when title
       (str title " - "))
     "Clojars"]
    (map #(include-css (str "/stylesheets/" %))
         ;; Bootstrap was customized to only include the 'grid' styles
         ;; (then the default colors were removed)
         ;; more info: http://getbootstrap.com/css/#grid
         ["reset.css" "vendor/bootstrap/bootstrap.css" "screen.css"])
    (include-js "https://use.typekit.net/zhw0tse.js")
    (typekit-js)
    (google-analytics-js)
    (raw (when-ie (include-js "/js/html5.js")))]
   [:body.container-fluid
    [:div#content-wrapper
     [:header.small-header.row
      [:div.home.col-xs-6.col-sm-3
       (link-to "/" (helpers/retinized-image "/images/clojars-logo-tiny.png" "Clojars"))
       [:h1 (link-to "/" "Clojars")]]
      [:div.col-xs-6.col-sm-3
       [:form {:action "/search"}
        [:input {:type "search"
                 :name "q"
                 :id "search"
                 :class "search"
                 :placeholder "Search projects..."
                 :value (:query ctx)
                 :required true}]]]
      [:nav.main-navigation.col-xs-12.col-sm-6
       (if (:account ctx)
         (unordered-list
          [(link-to "/" "dashboard")
           (link-to "/profile" "profile")
           (link-to "/logout" "logout")])
         (unordered-list
          [(link-to "/login" "login")
           (link-to "/register" "register")]))]]
     body
     (footer (get ctx :footer-links? true))]]))

(defn html-doc-with-large-header [title ctx & body]
  (html5 {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:link {:type "application/opensearchdescription+xml"
            :title "Clojars"
            :href "/opensearch.xml"
            :rel "search"}]

    [:link {:rel "apple-touch-icon" :sizes "57x57" :href "/apple-touch-icon-57x57.png?v=47K2kprJd7"}]
    [:link {:rel "apple-touch-icon" :sizes "60x60" :href "/apple-touch-icon-60x60.png?v=47K2kprJd7"}]
    [:link {:rel "apple-touch-icon" :sizes "72x72" :href "/apple-touch-icon-72x72.png?v=47K2kprJd7"}]
    [:link {:rel "apple-touch-icon" :sizes "76x76" :href "/apple-touch-icon-76x76.png?v=47K2kprJd7"}]
    [:link {:rel "apple-touch-icon" :sizes "114x114" :href "/apple-touch-icon-114x114.png?v=47K2kprJd7"}]
    [:link {:rel "apple-touch-icon" :sizes "120x120" :href "/apple-touch-icon-120x120.png?v=47K2kprJd7"}]
    [:link {:rel "apple-touch-icon" :sizes "144x144" :href "/apple-touch-icon-144x144.png?v=47K2kprJd7"}]
    [:link {:rel "apple-touch-icon" :sizes "152x152" :href "/apple-touch-icon-152x152.png?v=47K2kprJd7"}]
    [:link {:rel "icon" :type "image/png" :href "/favicon-32x32.png?v=47K2kprJd7" :sizes "32x32"}]
    [:link {:rel "icon" :type "image/png" :href "/favicon-96x96.png?v=47K2kprJd7" :sizes "96x96"}]
    [:link {:rel "icon" :type "image/png" :href "/favicon-16x16.png?v=47K2kprJd7" :sizes "16x16"}]
    [:link {:rel "manifest" :href "/manifest.json?v=47K2kprJd7"}]
    [:link {:rel "shortcut icon" :href "/favicon.ico?v=47K2kprJd7"}]
    [:meta {:name "apple-mobile-web-app-title" :content "Clojars"}]
    [:meta {:name "application-name" :content "Clojars"}]
    [:meta {:name "msapplication-TileColor" :content "#da532c"}]
    [:meta {:name "msapplication-TileImage" :content "/mstile-144x144.png?v=47K2kprJd7"}]
    [:meta {:name "theme-color" :content "#ffffff"}]

    [:meta {:name "viewport" :content "width=device-width,initial-scale=1"}]
    (structured-data/meta-tags (assoc ctx :title (if title
                                                   title
                                                   "Clojars")))
    [:title
     (when title
       (str title " - "))
     "Clojars"]
    (map #(include-css (str "/stylesheets/" %))
         ;; Bootstrap was customized to only include the 'grid' styles
         ;; (then the default colors were removed)
         ;; more info: http://getbootstrap.com/css/#grid
         ["reset.css" "vendor/bootstrap/bootstrap.css" "screen.css"])
    (include-js "https://use.typekit.net/zhw0tse.js")
    (typekit-js)
    (google-analytics-js)
    (raw (when-ie (include-js "/js/html5.js")))]
   [:body.container-fluid
    [:div.hero.row
     [:header
      [:div.home.col-xs-12.col-sm-6
       (link-to "/" (helpers/retinized-image "/images/clojars-logo.png" "Clojars"))
       [:h1
        (link-to "/" "Clojars")]]
      [:nav.main-navigation.col-xs-12.col-sm-6
       (if (:account ctx)
         (unordered-list
          [(link-to "/" "dashboard")
           (link-to "/profile" "profile")
           (link-to "/logout" "logout")])
         (unordered-list
          [(link-to "/login" "login")
           (link-to "/register" "register")]))]
      [:h2.hero-text.row
       [:span.col-md-12
        [:span.heavy "Clojars"]
        " is a "
        [:span.heavy "dead easy"]
        " community repository for "]
       [:span.col-md-12
        " open source Clojure libraries."]]]
     [:div.search-form-container.col-xs-12
      [:form {:action "/search"}
       [:input {:type "search"
                :name "q"
                :id "search"
                :placeholder "Search projects..."
                :value (:query ctx)
                :autofocus true
                :required true}]
       [:input {:id "search-button"
                :value "Search"
                :type "submit"}]]]
     [:h2.getting-started.row
      [:span.col-md-12
       "To get started pushing your own project "
       (link-to "/register" "register")
       " and then"]
      [:span.col-md-12
       " check out the "
       (link-to "http://wiki.github.com/clojars/clojars-web/tutorial" "tutorial")
       ". Alternatively, "
       (link-to "/projects" "browse the repository")
       "."]]]
    body
    (footer (get ctx :footer-links? true))]))

(defn flash [& msg]
  (when (some some? msg)
    (into [:div#notice.info] msg)))

(defn error-list [errors]
  (when errors
    [:div#notice.error
     [:strong "Blistering barnacles!"]
     "  Something's not shipshape:"
     (unordered-list errors)]))

(defn tag [s]
  (raw (html [:span.tag s])))

(defn jar-url [jar]
  (if (= (:group_name jar) (:jar_name jar))
    (str "/" (:jar_name jar))
    (str "/" (:group_name jar) "/" (:jar_name jar))))

(defn group-is-name?
  "Is the group of the artifact the same as its name?"
  [jar]
  (= (:group_name jar) (:jar_name jar)))

(defn jar-name [jar]
  (if (group-is-name? jar)
    (:jar_name jar)
    (str (:group_name jar) "/" (:jar_name jar))))

(defn jar-fork? [jar]
  (re-find #"^org.clojars." (or
                             (:group_name jar)
                             (:group-id jar)
                             "")))

(def single-fork-notice
  [:p.fork-notice.hint
   "Note: this artifact is a non-canonical fork. See "
   (link-to "https://github.com/clojars/clojars-web/wiki/Groups" "the wiki")
   " for more details."])

(def collection-fork-notice
  [:p.fork-notice.hint
   "Note: artifacts in italics are non-canonical forks. See "
   (link-to "https://github.com/clojars/clojars-web/wiki/Groups" "the wiki")
   " for more details."])

(defn maven-search-link
  ([group-id artifact-id]
   (cond->> ""
     group-id    (str (format "g:\"%s\" " group-id))
     artifact-id (str (format "a:\"%s\" " artifact-id))
     true        (str "|ga|1|")
     true        (url-encode)
     true        (str "http://search.maven.org/#search"))))

(defn shadow-notice [group-id artifact-id]
  [:div#notice.info
   "This artifact may shadow a release on Maven Central. You should "
   (link-to (maven-search-link group-id artifact-id)
     "search there")
   " for a canonical release."])

(def jar-notices
  (delay (-> "jar-notices.edn" io/resource slurp edn/read-string)))

(defn jar-notice-for [group-id artifact-id]
  (@jar-notices (symbol (format "%s/%s" group-id artifact-id))))

(defn jar-notice [group-id artifact-id]
  (if-let [notice (jar-notice-for group-id artifact-id)]
    [:div#notice.info (raw notice)]
    (if (and
          (not (maven/can-shadow-maven? group-id artifact-id))
          (maven/exists-on-central? group-id artifact-id))
      (shadow-notice group-id artifact-id))))

(defn jar-link [jar]
  (link-to {:class (when (jar-fork? jar) "fork")}
           (jar-url jar)
           (jar-name jar)))

(defn user-link [username]
  (link-to (str "/users/" username) username))

(defn group-link [groupname]
  (link-to (str "/groups/" groupname) groupname))

(defn format-date [s]
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") s))

(defn simple-date [s]
  (.format (java.text.SimpleDateFormat. "MMM d, yyyy") s))

(defn page-nav [current-page total-pages & {:keys [base-path] :or {base-path "/projects?page="}}]
  (let [previous-text (raw "&#8592; Previous")
        next-text (raw "Next &#8594;")
        page-range 3
        page-url base-path
        current-page (-> current-page (max 1) (min total-pages))

        main-div [:div.page-nav]
        previous-page (if (= current-page 1)
                        [[:span.previous-page.disabled previous-text]]
                        [[:a.previous-page
                          {:href (str page-url (- current-page 1))}
                          previous-text]])
        before-current (map
                        #(link-to (str page-url %) %)
                        (drop-while
                         #(< % 1)
                         (range (- current-page page-range) current-page)))
        current [[:em.current (str current-page)]]
        after-current (map
                       #(link-to (str page-url %) %)
                       (take-while
                        #(<= % total-pages)
                        (range (inc current-page) (+ current-page 1 page-range))))
        next-page (if (= current-page total-pages)
                    [[:span.next-page.disabled next-text]]
                    [[:a.next-page
                      {:href (str page-url (+ current-page 1))}
                      next-text]])]
    (vec
      (concat main-div previous-page before-current current after-current next-page))))

(defn page-description [current-page per-page total]
  (let [total-pages (-> (/ total per-page) Math/ceil .intValue)
        current-page (-> current-page (max 1) (min total-pages))
        upper (* per-page current-page)]
   [:div {:class "page-description"}
     "Displaying projects "
     [:b (str (-> upper (- per-page) inc) " - " (min upper total))]
     " of "
     [:b total]]))

(defn xml-escape [s]
  (str/escape s {\' "&apos;" \& "&amp;" \< "&lt;" \> "&gt;"}))
