'use strict';

import $ from 'jquery';

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
        let windowHeight = $(window).height();
        let footerHeight = $('.main-footer').outerHeight();
        let headerHeight = $('.main-header').height();

        elem.css('min-height', windowHeight - headerHeight - footerHeight);
      }, 500);

      angular.element($window).bind('resize', function() {
        let windowHeight = $(window).height();
        let footerHeight = $('.main-footer').outerHeight();
        let headerHeight = $('.main-header').height();

        elem.css('min-height', windowHeight - headerHeight - footerHeight);
      });
    }
  }
}
