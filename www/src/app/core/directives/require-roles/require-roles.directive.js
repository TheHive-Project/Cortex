'use strict';

export default function(app) {
  app.directive('requireRoles', requireRoles);

  function requireRoles($log, AuthService) {
    'ngInject';

    return {
      restrict: 'A',
      scope: false,
      link: linkFn
    };

    function linkFn(scope, elem, attrs) {
      let roles = (attrs['requireRoles'] || '').split(',');

      if (!AuthService.hasRole(roles || [])) {
        elem.remove();
      }
    }
  }
}
