/**
 * Controller for main page
 */
angular.module('cortex').controller('AppController',
    function($scope, $location, $state, AuthenticationSrv, NotificationService, currentUser) {
        'use strict';

        if(currentUser === 520) {
            $state.go('app.maintenance');
            return;
        }else if(!currentUser || !currentUser.id) {
            $state.go('login');
            return;
        }

        $scope.currentUser = currentUser;

        $scope.isAdmin = AuthenticationSrv.isAdmin;

        $scope.logout = function() {
            AuthenticationSrv.logout(function() {
                $state.go('login');
            }, function(data, status) {
                NotificationSrv.error('AppCtrl', data, status);
            });
        };
    }
);
