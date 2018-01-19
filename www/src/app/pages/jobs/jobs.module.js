'use strict';

import JobsController from './jobs.controller';
import tpl from './jobs.page.html';

import JobController from './job.controller';
import jobTpl from './job.page.html';

import JobDetailsController from './components/job.details.controller';
import jobDetailsTpl from './components/job.details.html';

const jobsModule = angular
  .module('jobs-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider
      .state('main.jobs', {
        url: 'jobs',
        component: 'jobsPage',
        resolve: {
          analyzers: AnalyzerService => AnalyzerService.list()
        }
      })
      .state('main.job-report', {
        url: 'jobs/{id}',
        component: 'jobsDetailsPage',
        resolve: {
          job: ($state, $q, $stateParams, $log, JobService) => {
            let defered = $q.defer();

            JobService.report($stateParams.id).then(
              function(response) {
                defered.resolve(response.data);
              },
              function(response) {
                $log.error('Error while getting job report');
                defered.reject(response);
                $state.go('main.jobs');
              }
            );

            return defered.promise;
          }
        }
      });
  })
  .component('jobsPage', {
    controller: JobsController,
    templateUrl: tpl,
    bindings: {
      analyzers: '<'
    }
  })
  .component('jobsDetailsPage', {
    controller: JobController,
    templateUrl: jobTpl,
    bindings: {
      job: '<'
    }
  })
  .component('jobDetails', {
    controller: JobDetailsController,
    templateUrl: jobDetailsTpl,
    bindings: {
      job: '<'
    }
  });

export default jobsModule;
