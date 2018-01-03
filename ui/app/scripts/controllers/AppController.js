/**
 * Controller for main page
 */
angular.module('cortex').controller('AppController', function($scope, $location, $state, AuthService, NotificationService, currentUser) {
    'use strict';

    if (currentUser === 520) {
        $state.go('app.maintenance');
        return;
    } else if (!currentUser || !currentUser.id) {
        $state.go('login');
        return;
    }

    $scope.currentUser = currentUser;

    $scope.isAdmin = AuthService.isAdmin;

    $scope.logout = function() {
        AuthService.logout(
            function() {
                $state.go('login');
            },
            function(data, status) {
                NotificationServiceNotificationSrv.error('AppCtrl', data, status);
            }
        );
    };
});
