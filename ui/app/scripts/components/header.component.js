(function() {
    'use strict';
    angular.module('cortex')
        .directive('header', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/header.component.html'                
            };
        });
})();