'use strict';

export default function(app) {
  app.directive('autoFocus', autofocus);

  function autofocus($timeout) {
    'ngInject';

    return {
      restrict: 'A',
      link: linkFn
    };

    function linkFn(scope, element, attrs) {
      if (attrs.autoFocus) {
        scope.$on(attrs.autoFocus, () => {
          $timeout(function() {
            element[0].focus();
          });
        });
      } else {
        $timeout(function() {
          element[0].focus();
        });
      }
    }
  }
}
