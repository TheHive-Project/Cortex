(function() {
    'use strict';
    angular.module('cortex')
        .directive('controlSidebar', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/control-sidebar.component.html'                
            };
        });
})();