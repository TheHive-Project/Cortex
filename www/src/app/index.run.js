'use strict';

import _ from 'lodash/core';

function runBlock($log, $transitions, $state) {
  'ngInject';

  $transitions.onSuccess({}, transition => {
    transition
      .injector()
      .get('$window')
      .scrollTo(0, 0);
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
    if (transition.error().detail.status === 520) {
      $state.go('maintenance');
    } else if (transition.error().detail.status === 401) {
      $state.go('login');
    }
  });
}

export default runBlock;
