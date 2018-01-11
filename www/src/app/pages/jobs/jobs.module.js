'use strict';

import JobsController from './jobs.controller';
import tpl from './jobs.page.html';

const jobsModule = angular
  .module('jobs-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('main.jobs', {
      url: 'jobs',
      component: 'jobsPage',
      resolve: {
        analyzers: AnalyzerService => AnalyzerService.list()
      }
    });
  })
  .component('jobsPage', {
    controller: JobsController,
    templateUrl: tpl,
    bindings: {
      analyzers: '<'
    }
  });

export default jobsModule;
