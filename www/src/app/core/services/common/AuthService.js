'use strict';

export default function(app) {
  function AuthService($http, $log, $q) {
    'ngInject';

    this.currentUser = null;

    this.login = function(username, password) {
      return $http.post('./api/login', {
        user: username,
        password: password
      });
    };

    this.logout = function() {
      return $http.get('./api/logout').then(
        /*data*/ () => {
          this.currentUser = null;
        }
      );
    };

    this.current = function() {
      return $http
        .get('./api/user/current')
        .then(response => {
          this.currentUser = response.data;

          return $q.resolve(this.currentUser);
        })
        .catch(err => {
          this.currentUser = null;

          return $q.reject(err);
        });
    };

    this.isAdmin = function(user) {
      let re = /admin/i;

      return re.test(user.roles);
    };
  }

  app.service('AuthService', AuthService);
}
