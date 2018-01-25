'use strict';

import AnalyzersController from './analyzers.controller';
import tpl from './analyzers.page.html';

import analyzerService from './analyzers.service.js';

const analyzersModule = angular
  .module('analyzers-module', ['ui.router'])
  .config(($stateProvider, Roles) => {
    'ngInject';

    $stateProvider.state('main.analyzers', {
      url: 'analyzers',
      component: 'analyzersPage',
      resolve: {
        analyzers: AnalyzerService => AnalyzerService.list()
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
      analyzers: '<',
      definitions: '<'
    }
  })
  .service('AnalyzerService', analyzerService);

export default analyzersModule;
