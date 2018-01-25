'use strict';

export default function(app) {
  app.directive('requireRoles', requireRoles);

  function requireRoles($log, AuthService) {
    'ngInject';

    return {
      restrict: 'A',
      scope: {
        roles: '='
      },
      link: linkFn
    };

    function linkFn(scope, elem) {
      if (!AuthService.hasRole(scope.roles || [])) {
        elem.remove();
      }
    }
  }
}
