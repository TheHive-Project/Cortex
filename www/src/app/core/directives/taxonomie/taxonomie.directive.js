'use strict';

import _ from 'lodash/core';

import tpl from './taxonomie.html';

export default function (app) {
  app.directive('taxonomie', taxonomie);

  function taxonomie() {
    'ngInject';

    return {
      templateUrl: tpl,
      scope: {
        taxonomies: '='
      },
      replace: true,
      link: linkFn
    };

    function linkFn(scope) {

    }
  }
}