'use strict';

function runBlock($log, $rootScope, $window) {
  'ngInject';

  $rootScope.$on('$stateChangeSuccess', function() {
    $window.scrollTo(0, 0);
  });

  $log.debug('Hello from run block!');
}

export default runBlock;
