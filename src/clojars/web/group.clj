(ns clojars.web.group
  (:require
   [clojars.auth :refer [authorized-admin? authorized-member?]]
   [clojars.db :refer [find-group-verification get-group-settings jars-by-groupname]]
   [clojars.web.common :refer [audit-table form-table html-doc jar-link user-link error-list verified-group-badge-small]]
   [clojars.web.safe-hiccup :refer [form-to]]
   [clojars.web.structured-data :as structured-data]
   [hiccup.element :refer [unordered-list]]
   [hiccup.form :refer [text-field hidden-field]]))

(def is-admin? :admin)

(defn show-group [db account groupname actives & errors]
  (let [admin? (authorized-admin? db account groupname)
        member? (authorized-member? db account groupname)
        show-membership-details? (or admin? member?)
        verified-group? (find-group-verification db groupname)
        group-settings (get-group-settings db groupname)]
    (html-doc (str groupname " group") {:account account :description (format "Clojars projects in the %s group" groupname)}
              [:div.col-xs-12
               (structured-data/breadcrumbs [{:url  (str "https://clojars.org/groups/" groupname)
                                              :name groupname}])
               [:div#group-title
                [:h1 (str groupname " group")]
                (when (and verified-group? show-membership-details?)
                  verified-group-badge-small)]
               [:h2 "Projects"]
               (unordered-list (map jar-link (jars-by-groupname db groupname)))
               [:h2 "Members"]
               (if show-membership-details?
                 [:table.group-member-list
                  [:thead
                   [:tr
                    [:th "Username"]
                    [:th "Admin?"]]]
                  [:tbody
                   (for [active (sort-by :user actives)]
                     [:tr
                      [:td (user-link (:user active))]
                      [:td
                       (if (is-admin? active)
                         "Yes"
                         "No")]
                      (when admin?
                        (list
                         [:td
                          (cond
                            (= account (:user active)) ""
                            (is-admin? active)
                            (form-to [:post (str "/groups/" groupname)]
                                     (hidden-field "username" (:user active))
                                     (hidden-field "admin" 0)
                                     [:input.button {:type "submit" :value "Toggle Admin"}])
                            :else
                            (form-to [:post (str "/groups/" groupname)]
                                     (hidden-field "username" (:user active))
                                     (hidden-field "admin" 1)
                                     [:input.button.green-button {:type "submit" :value "Toggle Admin"}]))]
                         [:td
                          (if (= account (:user active))
                            ""
                            (form-to [:delete (str "/groups/" groupname)]
                                     (hidden-field "username" (:user active))
                                     [:input.button.red-button {:type "submit" :value "Remove Member"}]))]))])]]
                 (unordered-list (map user-link (sort (map :user actives)))))
               (error-list errors)
               (when admin?
                 (list
                  [:div.add-member
                   [:h2 "Add member to group"]
                   (form-table
                    [:post (str "/groups/" groupname)]
                    [[[:label "Username "]
                      (text-field "username")]
                     [[:label "Admin? "]
                      [:input {:type "checkbox"
                               :name "admin"
                               :id "admin"
                               :value 1
                               :checked false}]]]
                    [:input.button {:type "submit" :value "Add Member"}])]
                  [:div.group-settings
                   [:h2 "Group Settings"]
                   (form-table
                    [:post (format "/groups/%s/settings" groupname)]
                    [[[:label "Require users to have two-factor auth enabled to deploy? "]
                      [:input {:type "checkbox"
                               :name "require_mfa"
                               :id "require_mfa"
                               :value 1
                               :checked (:require_mfa_to_deploy group-settings false)}]]]
                    [:input.button {:type "submit" :value "Update Settings"}])]))
               (when show-membership-details?
                 (audit-table db groupname {:group-name groupname}))])))
