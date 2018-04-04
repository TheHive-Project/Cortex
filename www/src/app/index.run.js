'use strict';

import _ from 'lodash/core';

function runBlock(
  $log,
  $q,
  $transitions,
  $state,
  AuthService,
  Roles,
  NotificationService
) {
  'ngInject';

  $transitions.onSuccess({}, transition => {
    transition
      .injector()
      .get('$window')
      .scrollTo(0, 0);
  });

  $transitions.onBefore({ to: 'index' }, transition => {
    const stateService = transition.router.stateService;

    return AuthService.current()
      .then(user => {
        if (user.roles.indexOf(Roles.SUPERADMIN) !== -1) {
          return stateService.target('main.organizations');
        } else {
          return stateService.target('main.jobs');
        }
      })
      .catch(err => $q.reject(err));
  });

  $transitions.onBefore({}, transition => {
    let roles = (transition.to().data && transition.to().data.allow) || [];
    let auth = transition.injector().get('AuthService');

    if (auth.currentUser === null) {
      return;
    }

    if (!_.isEmpty(roles) && !auth.hasRole(roles)) {
      return transition.router.stateService.target('main.jobs');
    }
  });

  $transitions.onError({}, transition => {
    if (!transition.error().detail) {
      return;
    }

    let status = transition.error().detail.status;

    if (status === 520) {
      $state.go('maintenance');
    } else if (status === 401) {
      $state.go('login');
    } else {
      NotificationService.handleError(
        'Error',
        transition.error().detail.data,
        status
      );
    }
  });
}

export default runBlock;
