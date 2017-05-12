(function() {
    'use strict';
    angular.module('cortex')
        .directive('appContainer', function() {
            return {
                restrict: 'E',
                templateUrl: 'views/components/app-container.component.html'                ,
                controller: function(VersionSrv) {
                    var self = this;
                    self.config = {};

                    VersionSrv.get()
                        .then(function(response) {
                            self.config = response.data;
                        });
                },
                controllerAs: '$vm'
            };
        });
})();
