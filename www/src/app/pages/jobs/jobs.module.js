'use strict';

import _ from 'lodash/core';

import JobsController from './jobs.controller';
import tpl from './jobs.page.html';

import JobController from './job.controller';
import jobTpl from './job.page.html';

import JobDetailsController from './components/job.details.controller';
import jobDetailsTpl from './components/job.details.html';

import JobsListController from './components/jobs.list.controller';
import jobsListTpl from './components/jobs.list.html';
import './components/jobs.list.scss';

import jobService from './jobs.service';

import './jobs.page.scss';

const jobsModule = angular
  .module('jobs-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider
      .state('main.jobs', {
        url: 'jobs',
        component: 'jobsPage',
        resolve: {
          datatypes: ($q, AnalyzerService) => AnalyzerService.list()
            .then(() => $q.resolve(AnalyzerService.getTypes()))
            .catch(err => $q.reject(err)),
          jobtypes: () => ['analyzer', 'responder'],
          analyzers: AnalyzerService =>
            AnalyzerService.list().then(analyzers =>
              _.sortBy(
                _.map(analyzers, a =>
                  _.pick(
                    a,
                    'name',
                    'id',
                    'dataTypeList',
                    'analyzerDefinitionId'
                  )
                ),
                'name'
              )
            )
        }
      })
      .state('main.job-report', {
        url: 'jobs/{id}',
        component: 'jobsDetailsPage',
        resolve: {
          job: ($state, $q, $stateParams, $log, JobService) => {
            let defered = $q.defer();

            JobService.report($stateParams.id).then(
              function (response) {
                defered.resolve(response.data);
              },
              function (response) {
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
      datatypes: '<',
      analyzers: '<',
      jobtypes: '<'
    }
  })
  .component('jobsList', {
    controller: JobsListController,
    templateUrl: jobsListTpl,
    bindings: {
      jobs: '<',
      onDelete: '&'
    }
  })
  .component('jobsDetailsPage', {
    controller: JobController,
    templateUrl: jobTpl,
    bindings: {
      job: '<'
    },
    require: {
      main: '^^mainPage'
    }
  })
  .component('jobDetails', {
    controller: JobDetailsController,
    templateUrl: jobDetailsTpl,
    bindings: {
      job: '<'
    }
  })
  .service('JobService', jobService);

export default jobsModule;