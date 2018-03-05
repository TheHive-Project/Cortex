'use strict';

import tpl from './user-avatar.html';
import './user-avatar.scss';

export default function(app) {
  app.directive('userAvatar', userAvatar);

  function userAvatar($log, UserService) {
    'ngInject';

    return {
      templateUrl: tpl,
      scope: {
        user: '=userId',
        prefix: '=',
        iconOnly: '=',
        iconSize: '@'
      },
      link: linkFn
    };

    function linkFn(scope) {
      scope.userInfo = UserService;
      scope.initials = '';

      scope.$watch('userData.name', value => {
        if (!value) {
          return;
        }

        scope.initials = value
          .split(' ')
          .map(item => item[0])
          .join('')
          .substr(0, 3)
          .toUpperCase();
      });

      scope.$watch('user', value => {
        if (!value) {
          return;
        }
        scope.userInfo.getCache(value).then(userData => {
          scope.userData = userData;
        });
      });
    }
  }
}
