(ns clojars.integration.steps
  (:require [cemerick.pomegranate.aether :as aether]
            [clojars.config :refer [config]]
            [clojars.db :as db]
            [clojars.maven :as maven]
            [clojars.test-helper :as help]
            [clojure.java.io :as io]
            [kerodon.core :refer [choose fill-in follow follow-redirect press visit]]
            [net.cgrand.enlive-html :as enlive])
  (:import java.io.File))

(defn login-as [state user password]
  (-> state
      (visit "/")
      (follow "login")
      (fill-in "Username" user)
      (fill-in "Password" password)
      (press "Login")))

(defn register-as
  ([state user email password]
     (register-as state user email password password))
  ([state user email password confirm]
     (-> state
         (visit "/")
         (follow "register")
         (fill-in "Email" email)
         (fill-in "Username" user)
         (fill-in "Password" password)
         (fill-in "Confirm password" confirm)
         (press "Register"))))

(defn create-deploy-token
  ([state user password token-name]
   (create-deploy-token state user password token-name nil))
  ([state user password token-name scope]
   (-> state
       (login-as user password)
       (follow-redirect)
       (follow "deploy tokens")
       (fill-in "Token name" token-name)
       (cond-> scope (choose "Token scope" scope))
       (press "Create Token")
       :enlive
       (enlive/select [:div.new-token :> :pre])
       (first)
       (enlive/text))))

(defn file-repo [path]
  (str (.toURI (File. path))))

(defn inject-artifacts-into-repo! [db user jar pom]
  (let [pom-file (if (instance? java.io.File pom)
                   pom
                   (io/resource pom))
        jarmap   (maven/pom-to-map pom-file)]
    (db/add-jar db user jarmap)
    (aether/deploy :coordinates [(keyword (:group jarmap)
                                          (:name jarmap))
                                 (:version jarmap)]
                   :jar-file (io/resource jar)
                   :pom-file pom-file
                   :local-repo help/local-repo
                   :repository {"local" (file-repo (:repo (config)))})))
