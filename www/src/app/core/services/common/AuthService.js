'use strict';

import _ from 'lodash';

export default class AuthService {
  constructor($http, $log, $q) {
    'ngInject';

    this.$log = $log;
    this.$q = $q;
    this.$http = $http;

    this.currentUser = null;
  }

  login(username, password) {
    return this.$http.post('./api/login', {
      user: username,
      password: password
    });
  }

  logout() {
    return this.$http.get('./api/logout').then(
      /*data*/ () => {
        this.currentUser = null;
      }
    );
  }

  current() {
    return this.$http
      .get('./api/user/current')
      .then(response => {
        this.currentUser = response.data;

        return this.$q.resolve(this.currentUser);
      })
      .catch(err => {
        this.currentUser = null;

        return this.$q.reject(err);
      });
  }

  isAdmin(user) {
    let re = /admin/i;

    return re.test(user.roles);
  }

  hasRole(roles) {
    if (!this.currentUser) {
      return false;
    }

    return !_.isEmpty(_.intersection(this.currentUser.roles, roles));
  }
}
