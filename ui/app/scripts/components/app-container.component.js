(function() {
    'use strict';
    angular.module('cortex')
        .directive('appContainer', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/app-container.component.html'                
            };
        });
})();