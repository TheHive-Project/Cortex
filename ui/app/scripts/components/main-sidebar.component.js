(function() {
    'use strict';
    angular.module('cortex')
        .directive('mainSidebar', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/main-sidebar.component.html'                
            };
        });
})();