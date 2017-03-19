(ns clojars.test.unit.db
  (:require [clojars.db :as db]
            [clojure.java.jdbc :as jdbc]
            [clojars.test.test-helper :as help]
            [clj-time.core :as time]
            [clojure.test :refer :all]))

(use-fixtures :each
  help/using-test-config
  help/with-clean-database)

(defn submap [s m]
  (every? (fn [[k v]] (= (get m k) v)) s))

(deftest submap-test
  (is (not (submap {:a 1} nil)))
  (is (not (submap {:a 1} {:a 2})))
  (is (submap {:a 1} {:a 1 :b 2})))

(deftest added-users-can-be-found
  (let [email "test@example.com"
        name "testuser"
        password "password"]
      (db/add-user help/*db* email name password)
      (are [x] (submap {:email email
                        :user name}
                       x)
           (db/find-user help/*db* name)
           (db/find-user-by-user-or-email help/*db* name)
           (db/find-user-by-user-or-email help/*db* email))))

(deftest user-does-not-exist
  (is (not (db/find-user-by-user-or-email help/*db* "test2@example.com"))))

(deftest added-users-can-be-found-by-password-reset-code-except-when-expired
  (let [email "test@example.com"
        name "testuser"
        password "password"]
      (db/add-user help/*db* email name password)
      (let [reset-code (db/set-password-reset-code! help/*db* "testuser")]
        (is (submap {:email email
                     :user name
                     :password_reset_code reset-code}
                    (db/find-user-by-password-reset-code help/*db* reset-code)))

        (time/do-at (-> 1 time/days time/from-now)
          (is (not (db/find-user-by-password-reset-code help/*db* reset-code)))))))

(deftest updated-users-can-be-found
  (let [email "test@example.com"
        name "testuser"
        password "password"
        ms (long 0)
        email2 "test2@example.com"
        name2 "testuser2"
        password2 "password2"]
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. ms))]
      (db/add-user help/*db* email name password)
      (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 1)))]
        (db/update-user help/*db* name email2 name2 password2)
        (are [x] (submap {:email email2
                          :user name2
                          :created ms}
                         x)
             (db/find-user help/*db* name2)
             (db/find-user-by-user-or-email help/*db* name2)
             (db/find-user-by-user-or-email help/*db* email2)))
      (is (not (db/find-user help/*db* name))))))

(deftest update-user-works-when-password-is-blank
  (let [email "test@example.com"
        name "testuser"
        password "password"
        email2 "test2@example.com"
        name2 "testuser2"
        password2 ""]
    (db/add-user help/*db* email name password)
    (let [old-user (db/find-user help/*db* name)]
      (db/update-user help/*db* name email2 name2 password2)
      (let [user (db/find-user help/*db* name2)]
        (is (= email2 (:email user)))
        (is (= (:password old-user) (:password user)))))))

(deftest added-users-are-added-only-to-their-org-clojars-group-as-admins
  (let [email "test@example.com"
        name "testuser"
        password "password"]
    (db/add-user help/*db* email name password)
    (is (= ["testuser"] (db/group-adminnames help/*db* (str "org.clojars." name))))
    (is (= ["testuser"] (db/group-activenames help/*db* (str "org.clojars." name))))
    (is (= [] (db/group-membernames help/*db* (str "org.clojars." name))))
    (is (= ["org.clojars.testuser"] (db/find-groupnames help/*db* name)))))

(deftest members-can-be-added-to-groups
  (let [email "test@example.com"
        name "testuser"
        password "password"]
    (db/add-user help/*db* email name password)
    (db/add-member help/*db* "test-group" name "some-dude")
    (is (= ["testuser"] (db/group-activenames help/*db* "test-group")))
    (is (= ["testuser"] (db/group-membernames help/*db* "test-group")))
    (is (= [] (db/group-adminnames help/*db* "test-group")))
    (is (some #{"test-group"} (db/find-groupnames help/*db* name)))))

(deftest admins-can-be-added-to-groups
  (let [email "test@example.com"
        name "testadmin"
        password "password"]
    (db/add-user help/*db* email name password)
    (db/add-admin help/*db* "test-group" name "some-dude")
    (is (= ["testadmin"] (db/group-activenames help/*db* "test-group")))
    (is (= [] (db/group-membernames help/*db* "test-group")))
    (is (= ["testadmin"] (db/group-adminnames help/*db* "test-group")))
    (is (some #{"test-group"} (db/find-groupnames help/*db* name)))))

;;TODO: Tests below should have the users added first.
;;Currently user unenforced foreign keys are by name
;;so these are faking relationships

(deftest added-jars-can-be-found
  (let [name "tester"
        ms (long 0)
        jarmap {:name name :group name :version "1.0"
                :description "An dog awesome and non-existent test jar."
                :homepage "http://clojars.org/"
                :authors ["Alex Osborne" "a little fish"]}
        result {:jar_name name
                :version "1.0"
                :homepage "http://clojars.org/"
                :scm nil
                :user "test-user"
                :created ms
                :group_name name
                :authors "Alex Osborne, a little fish"
                :description "An dog awesome and non-existent test jar."}]
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. ms))]
      (db/add-jar help/*db* "test-user" jarmap)
      (are [x] (submap result x)
        (db/find-jar help/*db* name name)
        (first (db/jars-by-groupname help/*db* name))
        (first (db/jars-by-username help/*db* "test-user"))))))

(deftest added-jars-store-dependencies
  (let [name "tester"
        ms (long 0)
        jarmap {:name name :group name :version "1.0"
                :description "An dog awesome and non-existent test jar."
                :homepage "http://clojars.org/"
                :authors ["Alex Osborne" "a little fish"]
                :dependencies [{:group_name "foo" :jar_name "bar" :version "1" :scope "test"}]}]
    (db/add-jar help/*db* "test-user" jarmap)
    (let [deps (db/find-dependencies help/*db* name name "1.0")]
      (is (= 1 (count deps)))
      (is (submap
            {:jar_name       name
             :group_name     name
             :version        "1.0"
             :dep_jar_name   "bar"
             :dep_group_name "foo"
             :dep_version    "1"
             :dep_scope      "test"}
            (first deps))))))

(deftest added-snapshot-jars-do-not-duplicate-dependencies
  (let [name "tester"
        ms (long 0)
        jarmap {:name name :group name :version "1.0-SNAPSHOT"
                :description "An dog awesome and non-existent test jar."
                :homepage "http://clojars.org/"
                :authors ["Alex Osborne" "a little fish"]
                :dependencies [{:group_name "foo" :jar_name "bar" :version "1" :scope "test"}]}]
    (db/add-jar help/*db* "test-user" jarmap)
    (db/add-jar help/*db* "test-user" jarmap)
    (let [deps (db/find-dependencies help/*db* name name "1.0-SNAPSHOT")]
      (is (= 1 (count deps)))
      (is (submap
            {:jar_name       name
             :group_name     name
             :version        "1.0-SNAPSHOT"
             :dep_jar_name   "bar"
             :dep_group_name "foo"
             :dep_version    "1"
             :dep_scope      "test"}
            (first deps))))))

(deftest jars-can-be-deleted-by-group
  (let [group "foo"
        jar {:name "one" :group group :version "1.0"
             :description "An dog awesome and non-existent test jar."
             :homepage "http://clojars.org/"
             :authors ["Alex Osborne" "a little fish"]
             :dependencies [{:group_name "foo" :jar_name "bar" :version "1" :scope "test"}]}]
    (db/add-jar help/*db* "test-user" jar)
    (db/add-jar help/*db* "test-user"
      (assoc jar
        :name "two"))
    (db/add-jar help/*db* "test-user"
      (assoc jar
        :group "another"))
    (is (= 2 (count (db/jars-by-groupname help/*db* group))))
    (db/delete-jars help/*db* group)
    (is (empty? (db/jars-by-groupname help/*db* group)))
    (is (empty? (db/find-dependencies help/*db* group "one" "1.0")))
    (is (= 1 (count (db/jars-by-groupname help/*db* "another"))))))

(deftest jars-can-be-deleted-by-group-and-jar-id
  (let [group "foo"
        jar {:name "one" :group group :version "1.0"
             :description "An dog awesome and non-existent test jar."
             :homepage "http://clojars.org/"
             :authors ["Alex Osborne" "a little fish"]
             :dependencies [{:group_name "foo" :jar_name "bar" :version "1" :scope "test"}]}]
    (db/add-jar help/*db* "test-user" jar)
    (db/add-jar help/*db* "test-user"
      (assoc jar
        :name "two"))
    (is (= 2 (count (db/jars-by-groupname help/*db* group))))
    (db/delete-jars help/*db* group "one")
    (is (= 1 (count (db/jars-by-groupname help/*db* group))))
    (is (empty? (db/find-dependencies help/*db* group "one" "1.0")))))

(deftest jars-can-be-deleted-by-group-and-jar-id-and-version
  (let [group "foo"
        jar {:name "one" :group group :version "1.0"
             :description "An dog awesome and non-existent test jar."
             :homepage "http://clojars.org/"
             :authors ["Alex Osborne" "a little fish"]
             :dependencies [{:group_name "foo" :jar_name "bar" :version "1" :scope "test"}]}]

    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 0)))]
                 (db/add-jar help/*db* "test-user" jar))
    (db/jars-by-groupname help/*db* group)
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 1)))]
                (db/add-jar help/*db* "test-user"
                            (assoc jar
                                   :version "2.0")))
    (db/jars-by-groupname help/*db* group)
    (is (= "2.0" (-> (db/jars-by-groupname help/*db* group) first :version)))
    (db/delete-jars help/*db* group "one" "2.0")
    (is (= "1.0" (-> (db/jars-by-groupname help/*db* group) first :version)))
    (is (empty? (db/find-dependencies help/*db* group "one" "2.0")))))

(deftest jars-by-group-only-returns-most-recent-version
  (let [name "tester"
        jarmap {:name name :group name :version "1" }
        result {:jar_name name
                :version "2"
                :user "test-user"
                :group_name name }]
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 0))]
      (db/add-jar help/*db* "test-user" jarmap)
      (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 1))]
        (db/add-jar help/*db* "test-user" (assoc jarmap :version "2"))))
    (let [jars (db/jars-by-groupname help/*db* name)]
      (dorun (map #(is (= %1 (select-keys %2 (keys %1)))) [result] jars))
      (is (= 1 (count jars))))))

(deftest jars-with-multiple-versions
  (let [name "tester"
        jarmap {:name name :group name :version "1" }]
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 0))]
      (db/add-jar help/*db* "test-user" jarmap))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 1))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :version "2")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 2))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :version "3")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 3))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :version "4-SNAPSHOT")))
    (is (= 4 (db/count-versions help/*db* name name)))
    (is (= ["4-SNAPSHOT" "3" "2" "1"]
           (map :version (db/recent-versions help/*db* name name))))
    (is (= ["4-SNAPSHOT"] (map :version (db/recent-versions help/*db* name name 1))))
    (is (= "3" (:version (db/find-jar help/*db* name name))))
    (is (= "4-SNAPSHOT" (:version (db/find-jar help/*db* name name "4-SNAPSHOT"))))))

(deftest jars-by-group-returns-all-jars-in-group
  (let [name "tester"
        jarmap {:name name :group name :version "1" }
        result {:jar_name name
                :version "1"
                :group_name name }]
    (db/add-member help/*db* name "test-user" "some-dude")
    (db/add-member help/*db* "tester-group" "test-user2" "some-dude")
    (db/add-member help/*db* name "test-user2" "some-dude")
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 0))]
      (db/add-jar help/*db* "test-user" jarmap))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 1))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :name "tester2")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 2))]
      (db/add-jar help/*db* "test-user2" (assoc jarmap :name "tester3")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 3))]
      (db/add-jar help/*db* "test-user2" (assoc jarmap :group "tester-group")))
    (let [jars (db/jars-by-groupname help/*db* name)]
      (dorun (map #(is (submap %1 %2))
                  [result
                   (assoc result :jar_name "tester2")
                   (assoc result :jar_name "tester3")]
                  jars))
      (is (= 3 (count jars))))))

(deftest jars-by-user-only-returns-most-recent-version
  (let [name "tester"
        jarmap {:name name :group name :version "1" }
        result {:jar_name name
                :version "2"
                :user "test-user"
                :group_name name }]
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 0))]
      (db/add-jar help/*db* "test-user" jarmap))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 1))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :version "2")))
    (let [jars (db/jars-by-username help/*db* "test-user")]
      (dorun (map #(is (= %1 (select-keys %2 (keys %1)))) [result] jars))
      (is (= 1 (count jars))))))

(deftest jars-by-user-returns-all-jars-by-user
  (let [name "tester"
        jarmap {:name name :group name :version "1" }
        result {:jar_name name
                :user "test-user"
                :version "1"
                :group_name name }]
    (db/add-member help/*db* name "test-user" "some-dude")
    (db/add-member help/*db* "tester-group" "test-user" "some-dude")
    (db/add-member help/*db* name "test-user2" "some-dude")
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 0))]
      (db/add-jar help/*db* "test-user" jarmap))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 1))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :name "tester2")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 2))]
      (db/add-jar help/*db* "test-user2" (assoc jarmap :name "tester3")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. 3))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :group "tester-group")))
    (let [jars (db/jars-by-username help/*db* "test-user")]
      (dorun (map #(is (submap %1 %2))
                  [result
                   (assoc result :jar_name "tester2")
                   (assoc result :group_name "tester-group")]
                  jars))
      (is (= 3 (count jars))))))

(deftest add-jar-validates-group-name-is-not-reserved
  (let [jarmap {:name "jar-name" :version "1"}]
    (doseq [group db/reserved-names]
      (is (thrown? Exception (db/add-jar help/*db* "test-user"
                                         (assoc jarmap :group group)))))))

(deftest add-jar-validates-group-permissions
    (let [jarmap {:name "jar-name" :version "1" :group "group-name"}]
      (db/add-member help/*db* "group-name" "some-user" "some-dude")
      (is (thrown? Exception (db/add-jar help/*db* "test-user" jarmap)))))


(deftest add-jar-creates-single-member-group-for-user-as-admin
    (let [jarmap {:name "jar-name" :version "1" :group "group-name"}]
      (is (empty? (db/group-activenames help/*db* "group-name")))
      (db/add-jar help/*db* "test-user" jarmap)
      (is (= ["test-user"] (db/group-adminnames help/*db* "group-name")))
      (is (= ["group-name"]
             (db/find-groupnames help/*db* "test-user")))))

(deftest recent-jars-returns-6-most-recent-jars-only-most-recent-version
  (let [name "tester"
        ms (long 0)
        jarmap {:name name :group name
                :description "An dog awesome and non-existent test jar."
                :homepage "http://clojars.org/"
                :authors ["Alex Osborne" "a little fish"]
                :version "1"}
        result {:user "test-user"
                :jar_name name
                :version "1"
                :homepage "http://clojars.org/"
                :scm nil
                :group_name name
                :authors "Alex Osborne, a little fish"
                :description "An dog awesome and non-existent test jar."}]
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 1)))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :name "1")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 2)))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :name "2")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 3)))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :name "3")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 4)))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :name "4")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 5)))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :version "5")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 6)))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :name "6")))
    (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 7)))]
      (db/add-jar help/*db* "test-user" (assoc jarmap :version "7")))
    (dorun (map #(is (submap %1 %2))
                [(assoc result :version "7")
                 (assoc result :jar_name "6")
                 (assoc result :jar_name "4")
                 (assoc result :jar_name "3")
                 (assoc result :jar_name "2")]
                (db/recent-jars help/*db*)))))

(deftest browse-projects-finds-jars
  (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 0)))]
    (db/add-jar help/*db* "test-user" {:name "rock" :group "jester" :version "0.1"})
    (db/add-jar help/*db* "test-user" {:name "rock" :group "tester" :version "0.1"}))
  (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 1)))]
    (db/add-jar help/*db* "test-user" {:name "rock" :group "tester" :version "0.2"})
    (db/add-jar help/*db* "test-user" {:name "paper" :group "tester" :version "0.1"}))
  (with-redefs [db/get-time (fn [] (java.sql.Timestamp. (long 2)))]
    (db/add-jar help/*db* "test-user" {:name "scissors" :group "tester" :version "0.1"}))
    ; tests group_name and jar_name ordering
    (is (=
          '({:version "0.1", :jar_name "rock", :group_name "jester"}
            {:version "0.1", :jar_name "paper", :group_name "tester"})
          (->>
            (db/browse-projects help/*db* 1 2)
            (map #(select-keys % [:group_name :jar_name :version])))))

    ; tests version ordering and pagination
    (is (=
          '({:version "0.2", :jar_name "rock", :group_name "tester"}
            {:version "0.1", :jar_name "scissors", :group_name "tester"})
          (->>
            (db/browse-projects help/*db* 2 2)
            ( map #(select-keys % [:group_name :jar_name :version]))))))

(deftest count-projects-works
  (db/add-jar help/*db* "test-user" {:name "rock" :group "jester" :version "0.1"})
  (db/add-jar help/*db* "test-user" {:name "rock" :group "tester" :version "0.1"})
  (db/add-jar help/*db* "test-user" {:name "rock" :group "tester" :version "0.2"})
  (db/add-jar help/*db* "test-user" {:name "paper" :group "tester" :version "0.1"})
  (db/add-jar help/*db* "test-user" {:name "scissors" :group "tester" :version "0.1"})
  (is (= (db/count-all-projects help/*db*) 4))
  (is (= (db/count-projects-before help/*db* "a") 0))
  (is (= (db/count-projects-before help/*db* "tester/rock") 2))
  (is (= (db/count-projects-before help/*db* "tester/rocks") 3))
  (is (= (db/count-projects-before help/*db* "z") 4)))

(deftest can-check-jar-exists
  (db/add-jar help/*db* "test-user" {:name "rock" :group "tester" :version "0.1"})
  (is (db/jar-exists help/*db* "tester" "rock"))
  (is (not (db/jar-exists help/*db* "tester" "paper"))))

;; TODO: recent-versions
