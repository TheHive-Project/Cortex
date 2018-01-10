'use strict';

import JobsController from './jobs.controller';
import tpl from './jobs.html';

const jobsModule = angular
  .module('jobs-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('main.jobs', {
      url: 'jobs',
      component: 'jobsList',
      resolve: {
        analyzers: AnalyzerService => AnalyzerService.list()
      }
    });
  })
  .component('jobsList', {
    controller: JobsController,
    templateUrl: tpl,
    bindings: {
      analyzers: '<'
    }
  });

export default jobsModule;
