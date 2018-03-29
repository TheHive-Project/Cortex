(function() {
    'use strict';
    angular.module('cortex').service('AuthenticationSrv', function($http) {
        var self = this;

        this.currentUser = null;

        this.login = function(username, password) {
            return $http.post('./api/login', {
                'user': username,
                'password': password
            });
        }

        this.logout = function(success, failure) {
            return $http.get('./api/logout').success(function(data, status) {
                self.currentUser = null;
            });
        }

        this.current = function(success, failure) {
            return $http.get('./api/user/current')
                .then(function(response, status) {
                    self.currentUser = response.data;

                    return self.currentUser;
                }, function(err) {
                    self.currentUser = null;
                });
        };

        this.isAdmin = function(user) {
            var u = user;
            var re = /admin/i;
            return re.test(u.roles);
        }

    });
})();
