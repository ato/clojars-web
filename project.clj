(defproject clojars-web "198"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/core.memoize "1.0.253"]
                 ;; manage jetty dependencies directly to make it easier to address CVEs
                 [org.eclipse.jetty/jetty-client "9.4.49.v20220914"]
                 [org.eclipse.jetty/jetty-server "9.4.49.v20220914"]
                 ;; manage jackson-databind directly to make it easiser to address CVEs
                 [com.fasterxml.jackson.core/jackson-databind "2.14.0-rc1"]
                 [raven-clj "1.6.0"
                  :exclusions [cheshire]]
                 [org.apache.maven/maven-model "3.8.4"]
                 [org.apache.maven/maven-repository-metadata "3.8.4"]
                 [org.codehaus.plexus/plexus-utils "3.4.1"]
                 [ring-middleware-format "0.7.4"
                  :exclusions [ring/ring-core
                               cheshire
                               com.fasterxml.jackson.core/jackson-core
                               com.fasterxml.jackson.dataformat/jackson-dataformat-smile
                               ;; newer version brought in by com.cognitect.aws/api
                               org.clojure/tools.reader
                               org.yaml/snakeyaml]]
                 ;; addresses CVEs
                 [org.yaml/snakeyaml "1.33"]
                 [org.apache.commons/commons-email "1.5"]
                 [net.cgrand/regex "1.0.1"
                  :exclusions [org.clojure/clojure]]
                 [com.cemerick/friend "0.2.3"
                  :exclusions [com.google.inject/guice
                               commons-codec
                               commons-io
                               commons-logging
                               org.apache.httpcomponents/httpclient
                               org.apache.httpcomponents/httpcore
                               org.clojure/core.cache
                               ring/ring-core
                               slingshot
                               ;; not used, excluded to address CVE-2007-1652, CVE-2007-1651
                               org.openid4java/openid4java-nodeps
                               ;; not used, excluded to address CVE-2012-0881, CVE-2013-4002, CVE-2009-2625
                               net.sourceforge.nekohtml/nekohtml
                               org.mindrot/jbcrypt]]
                 ;; addresses CVE-2015-0886
                 [org.mindrot/jbcrypt "0.4"]
                 [com.github.scribejava/scribejava-apis "8.3.1"
                  :exclusions [com.fasterxml.jackson.core/jackson-databind]]
                 [buddy/buddy-core "1.10.1"
                  :exclusions [commons-codec
                               cheshire]]
                 ;; addresses CVE-2020-28491
                 [cheshire "5.10.1"]
                 [clj-stacktrace "0.2.8"]
                 [clj-time "0.15.2"]
                 [ring/ring-defaults "0.3.3"
                  :exclusions [commons-codec
                               ;; newer version brought in by ring/ring-core
                               crypto-random]]
                 [valip "0.2.0"
                  :exclusions [commons-logging
                               commons-validator/commons-validator]]
                 ;; addresses CVE-2019-10086, CVE-2014-0114, CVE-2017-15708, CVE-2015-6420
                 [commons-validator/commons-validator "1.7"]
                 [org.apache.lucene/lucene-core "8.11.1"]
                 [org.apache.lucene/lucene-analyzers-common "8.11.1"]
                 [org.apache.lucene/lucene-queryparser "8.11.1"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [yesql "0.5.3"]
                 [org.postgresql/postgresql "42.4.1"]
                 [duct/hikaricp-component "0.1.2"
                  :exclusions [com.stuartsierra/component
                               org.slf4j/slf4j-api
                               org.slf4j/slf4j-nop]]
                 [duct "0.8.2"
                  :exclusions [org.clojure/tools.reader]]
                 [ring/ring-core "1.9.4"]
                 [ring/ring-jetty-adapter "1.9.4"]
                 [ring-jetty-component "0.3.1"
                  :exclusions [org.clojure/tools.reader
                               ring/ring-core]]
                 [digest "1.4.10"]
                 [clj-http "3.12.3"
                  :exclusions [commons-codec
                               commons-io]]
                 [aero "1.1.6"]
                 [one-time "0.7.0"
                  :exclusions [commons-codec
                               ;; not needed on java 17, addresses CWE-120
                               com.github.jai-imageio/jai-imageio-core
                               ;; not used, addresses CVE-2020-11987, CVE-2019-17566
                               org.apache.xmlgraphics/batik-dom
                               org.apache.xmlgraphics/batik-svggen]]

                 ;; logging
                 [org.clojure/tools.logging "1.2.4"]
                 [ch.qos.logback/logback-classic "1.3.0-alpha5"
                  :exclusions [com.sun.mail/javax.mail]]
                 ;; Upgrading for compatibility with logback 1.3.x
                 [org.slf4j/jcl-over-slf4j "2.0.0-alpha1"]

                 ;; AWS
                 [com.cognitect.aws/api "0.8.635"]
                 [com.cognitect.aws/endpoints "1.1.12.373"]
                 [com.cognitect.aws/s3 "825.2.1250.0"]
                 [com.cognitect.aws/ssm "825.2.1283.0"]]
  :plugins [[supersport "1"]]
  :main ^:skip-aot clojars.main
  :target-path "target/%s/"
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "super.sport/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "super.sport/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :aliases {"migrate" ["run" "-m" "clojars.tools.migrate-db" "development"]}
  :pedantic? :warn
  :profiles
  {:dev  [:project/dev :profiles/dev]
   :repl {:pedantic? false}
   :test [:project/test :profiles/test]
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:source-paths ["dev"]
                   :repl-options {:init-ns user
                                  :nrepl-middleware
                                  [sc.nrepl.middleware/wrap-letsc]}
                   :dependencies [[reloaded.repl "0.2.4"]
                                  [clj-commons/pomegranate "1.2.1"
                                   :exclusions
                                   [commons-logging
                                    org.apache.httpcomponents/httpcore]]
                                  [org.clojure/tools.namespace "1.2.0"]
                                  [eftest "0.5.9"]
                                  [kerodon "0.9.1"
                                   :exclusions [clj-time
                                                org.apache.httpcomponents/httpcore
                                                org.flatland/ordered
                                                org.jsoup/jsoup
                                                ring/ring-codec]]
                                  [net.polyc0l0r/bote "0.1.0"
                                   :exclusions [commons-codec
                                                javax.mail/mail
                                                org.clojars.kjw/slf4j
                                                org.clojars.kjw/slf4j-simple]]
                                  [nubank/matcher-combinators "3.3.1"
                                   ;; we don't use midje, so excluding it to
                                   ;; remove dep conflicts from its dependencies
                                   :exclusions [midje]]
                                  [vvvvalvalval/scope-capture-nrepl "0.3.1"]]
                   :resource-paths ["local-resources"]}
   :project/test  {}})
