'use strict';

import RepondersController from './responders.controller';
import tpl from './responders.page.html';

import RespondersListController from './components/responders.list.controller';
import respondersListTpl from './components/responders.list.html';

import responderService from './responders.service.js';

//import './analyzers.page.scss';

const respondersModule = angular
  .module('responders-module', ['ui.router'])
  .config(($stateProvider, Roles) => {
    'ngInject';

    $stateProvider.state('main.responders', {
      url: 'responders',
      component: 'respondersPage',
      resolve: {
        datatypes: ($q, ResponderService) => {
          let defer = $q.defer();

          ResponderService.list()
            .then(() => {
              defer.resolve(ResponderService.getTypes());
            })
            .catch(err => defer.reject(err));

          return defer.promise;
        }
      },
      data: {
        allow: [Roles.SUPERADMIN, Roles.ORGADMIN, Roles.ANALYZE]
      }
    });
  })
  .component('respondersPage', {
    controller: RepondersController,
    templateUrl: tpl,
    bindings: {
      datatypes: '<'
    }
  })
  .component('respondersList', {
    controller: RespondersListController,
    templateUrl: respondersListTpl,
    bindings: {
      responders: '<'
    }
  })
  .service('ResponderService', responderService);

export default respondersModule;