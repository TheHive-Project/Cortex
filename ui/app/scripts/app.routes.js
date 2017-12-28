(function () {
    'use strict';

    angular.module('cortex')
        .config(function ($stateProvider, $urlRouterProvider) {

            $urlRouterProvider.otherwise('/analyzers');

            $stateProvider.state('login', {
                url: '/login',
                templateUrl: 'views/login.html',
                controller: 'LoginController'
            });

            $stateProvider.state('app', {
                url: '/',
                abstract: true,
                templateUrl: 'views/app.html',
                controller: 'AppController',
                resolve: {
                    currentUser: function($q, $state, AuthenticationSrv) {
                        var deferred = $q.defer();

                        AuthenticationSrv.current()
                            .then(function(userData) {
                                return deferred.resolve(userData);
                            })
                            .catch(function(err, status) {
                                return deferred.resolve(status === 520 ? status : null);
                            });

                        return deferred.promise;
                    }
                }
            });

            $stateProvider
                .state('app.analyzers', {
                    url: 'analyzers',
                    templateUrl: 'views/analyzers.html',
                    controller: 'AnalyzersCtrl',
                    controllerAs: 'vm',
                    resolve: {
                        analyzers: function (AnalyzerSrv) {
                            return AnalyzerSrv.list();
                        }
                    }
                })
                .state('app.jobs', {
                    url: 'jobs',
                    templateUrl: 'views/jobs.html',
                    controller: 'JobsCtrl',
                    controllerAs: 'vm',
                    resolve: {
                        analyzers: function (AnalyzerSrv) {
                            return AnalyzerSrv.list();
                        }
                    }
                })
                .state('app.job-report', {
                    url: 'jobs/{id}',
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
                                    $state.go('app.jobs');
                                });

                            return defered.promise;
                        }
                    }
                });
        });
})();
