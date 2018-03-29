/**
 * Controller for login modal page2
 */
(function() {
  'use strict';
  angular.module('cortex').controller('LoginController', function($scope, $state, $uibModalStack, AuthService, NotificationService) {
    $scope.params = {};

    $uibModalStack.dismissAll();

    $scope.login = function() {
      $scope.params.username = angular.lowercase($scope.params.username);

      AuthService.login($scope.params.username, $scope.params.password)
        .then(function() {
          $state.go('app.analyzers');
        })
        .catch(function(err) {
          if (err.status === 520) {
            NotificationService.handleError('LoginController', err.data, err.status);
          } else {
            NotificationService.log(err.data, 'error');
          }
        });
    };
  });
})();
