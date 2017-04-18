(function () {
    'use strict';

    angular.module('cortex')
        .config(function ($stateProvider, $urlRouterProvider) {

            $urlRouterProvider.otherwise('/analyzers');

            $stateProvider
                .state('analyzers', {
                    url: '/analyzers',
                    templateUrl: 'views/analyzers.html',
                    controller: 'AnalyzersCtrl',
                    controllerAs: 'vm',
                    resolve: {
                        analyzers: function (AnalyzerSrv) {
                            return AnalyzerSrv.list();
                        }
                    }
                })
                .state('jobs', {
                    url: '/jobs',
                    templateUrl: 'views/jobs.html',
                    controller: 'JobsCtrl',
                    controllerAs: 'vm',
                    resolve: {
                        analyzers: function (AnalyzerSrv) {
                            return AnalyzerSrv.list();
                        }
                    }
                })
                .state('job-report', {
                    url: '/jobs/{id}',
                    templateUrl: 'views/jobs.report.html',
                    controller: 'JobReportCtrl',
                    controllerAs: 'vm',
                    resolve: {
                        job: function ($state, $q, $stateParams, $log, JobSrv) {
                            var defered = $q.defer();

                            JobSrv.report($stateParams.id)
                                .then(function (response) {
                                    defered.resolve(response.data);
                                }, function (response) {
                                    $log.error('Error while getting job report');
                                    defered.reject(response);
                                    $state.go('jobs');
                                });

                            return defered.promise;
                        }
                    }
                });
        });
})();
