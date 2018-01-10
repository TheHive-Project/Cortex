'use strict';

export default function(app) {
  app.directive('fixedHeight', fixedHeightDirective);

  function fixedHeightDirective($window, $timeout) {
    'ngInject';

    return {
      restrict: 'A',
      link: linkFn
    };

    function linkFn(scope, elem /*, attr*/) {
      $timeout(function() {
        let windowHeight = angular.element(window).height();
        let footerHeight = angular.element('.main-footer').outerHeight();
        let headerHeight = angular.element('.main-header').height();

        elem.css('min-height', windowHeight - headerHeight - footerHeight);
      }, 500);

      angular.element($window).bind('resize', function() {
        let windowHeight = angular.element(window).height();
        let footerHeight = angular.element('.main-footer').outerHeight();
        let headerHeight = angular.element('.main-header').height();

        elem.css('min-height', windowHeight - headerHeight - footerHeight);
      });
    }
  }
}
