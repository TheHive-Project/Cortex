'use strict';

import _ from 'lodash/core';

function runBlock($log, $transitions) {
  'ngInject';

  $transitions.onSuccess({}, trans => {
    trans
      .injector()
      .get('$window')
      .scrollTo(0, 0);
  });

  $transitions.onBefore({}, trans => {
    let roles = (trans.to().data && trans.to().data.allow) || [];
    let auth = trans.injector().get('AuthService');

    if (auth.currentUser === null) {
      return;
    }

    if (!_.isEmpty(roles) && !auth.hasRole(roles)) {
      return trans.router.stateService.target('main.jobs');
    }
  });
}

export default runBlock;
