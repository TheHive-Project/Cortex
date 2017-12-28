/**
 * Controller for login modal page2
 */
(function() {
    'use strict';
    angular.module('cortex').controller('LoginController', function($scope, $state, $uibModalStack, AuthenticationSrv, NotificationService) {
        $scope.params = {};

        $uibModalStack.dismissAll();

        $scope.login = function() {
            $scope.params.username = angular.lowercase($scope.params.username);

            AuthenticationSrv.login($scope.params.username, $scope.params.password)
                .then(function() {
                    $state.go('app.analyzers');
                })
                .catch(function(data, status) {
                    if (status === 520) {
                        NotificationService.handleError('LoginController', data, status);
                    } else {
                        NotificationService.log(data.message, 'error');
                    }
                });
        };
    });
})();
