'use strict';

import AnalyzersController from './analyzers.controller';
import tpl from './analyzers.page.html';

import AnalyzersListController from './components/analyzers.list.controller';
import analyzersListTpl from './components/analyzers.list.html';

import analyzerService from './analyzers.service.js';

import './analyzers.page.scss';

const analyzersModule = angular
  .module('analyzers-module', ['ui.router'])
  .config(($stateProvider, Roles) => {
    'ngInject';

    $stateProvider.state('main.analyzers', {
      url: 'analyzers',
      component: 'analyzersPage',
      resolve: {
        datatypes: ($q, AnalyzerService) => {
          let defer = $q.defer();

          AnalyzerService.list().then(() => {
            defer.resolve(AnalyzerService.getTypes());
          });

          return defer.promise;
        }
      },
      data: {
        allow: [Roles.ADMIN, Roles.WRITE]
      }
    });
  })
  .component('analyzersPage', {
    controller: AnalyzersController,
    templateUrl: tpl,
    bindings: {
      datatypes: '<'
      //analyzers: '<',
      //definitions: '<'
    }
  })
  .component('analyzersList', {
    controller: AnalyzersListController,
    templateUrl: analyzersListTpl,
    bindings: {
      analyzers: '<'
    }
  })
  .service('AnalyzerService', analyzerService);

export default analyzersModule;
