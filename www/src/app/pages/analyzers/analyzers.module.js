'use strict';

import AnalyzersController from './analyzers.controller';
import tpl from './analyzers.html';

const analyzersModule = angular
  .module('analyzers-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('main.analyzers', {
      url: 'analyzers',
      component: 'analyzersList',
      resolve: {
        analyzers: AnalyzerService => AnalyzerService.list(),
        definitions: AnalyzerService => AnalyzerService.definitions()
      }
    });
  })
  .component('analyzersList', {
    controller: AnalyzersController,
    templateUrl: tpl,
    bindings: {
      analyzers: '<',
      definitions: '<'
    }
  });

export default analyzersModule;
