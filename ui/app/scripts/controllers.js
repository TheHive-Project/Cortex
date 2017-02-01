'use strict';

/**
 * @ngdoc function
 * @name cortex.controller:MainCtrl
 * @description
 * # MainCtrl
 * Controller of the cortex
 */
angular.module('cortex')
    .controller('NavCtrl', function($q, $state, $uibModal, AnalyzerSrv, Notification) {
        this.newAnalysis = function () {

            AnalyzerSrv.list()
                .then(function(analyzers) {
                    var modalInstance = $uibModal.open({
                        animation: true,
                        templateUrl: 'views/analysis.form.html',
                        controller: 'AnalyzerFormCtrl',
                        controllerAs: 'vm',
                        size: 'lg',
                        resolve: {
                            initialData: function () {
                                return {
                                    analyzers: angular.copy(analyzers),
                                    dataTypes: _.keys(AnalyzerSrv.getTypes())
                                };
                            }
                        }
                    });

                    modalInstance.result.then(function (result) {
                        var artifact = _.omit(result, 'ids');

                        return $q.all(_.map(result.ids.split(','), function(analyzerId){
                            artifact.analyzer = {
                                id: analyzerId
                            };

                            return AnalyzerSrv.run(analyzerId, artifact);
                        }));
                    }).then(function (response) {
                        if($state.is('jobs')) {
                            $state.reload();
                        } else {
                            $state.go('jobs');
                        }
                        _.each(response, function(resp) {
                            Notification.success(resp.data.analyzerId + ' started successfully on ' + (resp.data.artifact.data || resp.data.artifact.attributes.filename));
                        });
                    });
                });
        };
    })
    .controller('AnalyzersCtrl', function ($state, $uibModal, $q, $log, AnalyzerSrv, Notification, analyzers) {
        this.search = {
            description: '',
            dataTypeList: ''
        };

        this.analyzers = analyzers;
        this.datatypes = AnalyzerSrv.getTypes();

        this.run = function (analyzer, dataType) {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/analyzers.form.html',
                controller: 'AnalyzerFormCtrl',
                controllerAs: 'vm',
                size: 'lg',
                resolve: {
                    initialData: function () {
                        return {
                            analyzer: angular.copy(analyzer),
                            dataType: angular.copy(dataType)
                        };
                    }
                }
            });

            modalInstance.result.then(function (result) {
                return AnalyzerSrv.run(result.analyzer.id, result);
            }).then(function (response) {
                $state.go('jobs');
                Notification.success(response.data.analyzerId + ' started successfully on ' + response.data.artifact.data);
            });
        };

        this.filterByType = function (type) {
            if (this.search.dataTypeList === type) {
                this.search.dataTypeList = '';
            } else {
                this.search.dataTypeList = type;
            }
        };

    })
    .controller('AnalyzerFormCtrl', function ($uibModalInstance, Tlps, initialData) {
        this.initialData = initialData;
        this.tlps = Tlps;
        this.formData = {
            analyzer: this.initialData.analyzer,
            tlp: Tlps[2],
            dataType: this.initialData.dataType
        };

        this.isFile = function () {
            return this.formData.dataType === 'file';
        };

        this.clearData = function () {
            delete this.formData.data;
            delete this.formData.attachment;
            delete this.formData.ids;

            _.each(this.initialData.analyzers, function (item) {
                item.active = false;
            });
        };

        this.toggleAnalyzer = function (analyzer) {
            analyzer.active = !analyzer.active;

            var active = _.filter(this.initialData.analyzers, function (item) {
                return item.active === true;
            });

            this.formData.ids = _.pluck(active, 'id').join(',');
        };

        this.ok = function () {
            $uibModalInstance.close(this.formData);
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    })
    .controller('JobsCtrl', function ($scope, $uibModal, $interval, JobSrv, AnalyzerSrv, Notification, _, analyzers) {
        var self = this;

        this.analyzers = analyzers;
        this.datatypes = AnalyzerSrv.getTypes();
        this.jobs = [];
        this.running = 0;
        this.pagination = {
            current: 1,
            total: 0,
            itemsPerPage: 50
        };

        this.search = {
            analyzerId: '',
            data: '',
            dataType: ''
        };

        this.load = function (page) {
            if(!page) {
                page = 1;
            }
            var post = {
                limit: this.pagination.itemsPerPage,
                start: page-1,
                dataTypeFilter: this.search.dataType || null,
                analyzerFilter: this.search.analyzerId || null,
                dataFilter: this.search.data || null
            };

            JobSrv.list(post).then(function (response) {
                self.jobs = response.data;
                self.running = _.findIndex(self.jobs, {
                    'status': 'InProgress'
                });
                self.pagination.total = parseInt(response.headers('x-total')) || 0;
                self.pagination.current = page;
            });
        };

        this.pageChanged = function(page) {
            this.load(page);
        };

        this.filterJobs = function (element) {

            var conditions = [];

            if (self.search.analyzerId) {
                conditions.push(element.analyzerId === self.search.analyzerId);
            }

            if (self.search.dataType) {
                conditions.push(element.artifact.attributes.dataType === self.search.dataType);
            }

            if (self.search.data) {
                var data = self.search.data;
                var attrs = element.artifact.attributes;
                var regex = new RegExp(data, 'gi');
                var artifact = (attrs.dataType === 'file') ? attrs.filename : element.artifact.data;

                conditions.push(regex.test(artifact));
            }

            return conditions.indexOf(false) < 0;
        };

        this.filterByAnalyzer = function (analyzerId) {
            if (this.search.analyzerId === analyzerId) {
                this.search.analyzerId = '';
            } else {
                this.search.analyzerId = analyzerId;
            }
            this.load(1);
        };

        this.filterByType = function (type) {
            if (this.search.dataType === type) {
                this.search.dataType = '';
            } else {
                this.search.dataType = type;
            }
            this.load(1);
        };

        this.clearDataFilter = function() {
            this.search.data = '';
            this.load(1);
        };

        this.remove = function (id) {
            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/jobs.delete.html',
                controller: 'JobDeleteCtrl',
                controllerAs: 'vm',
                resolve: {
                    jobId: function () {
                        return id;
                    }
                }
            });

            modalInstance.result.then(function (id) {
                return JobSrv.remove(id);
            }).then(function ( /*response*/ ) {
                self.load(1);
                Notification.success('Job removed successfully');
            });

        };

        this.checkJobs = function () {
            if (self.running !== -1) {
                self.load(self.pagination.current);
            }
        };

        this.load(1);

        this.timer = $interval(this.checkJobs, 10000);

        $scope.$on("$destroy", function () {
            $interval.cancel(self.timer);
        });
    })
    .controller('JobReportCtrl', function (job) {
        this.job = job;
    })
    .controller('JobDeleteCtrl', function ($uibModalInstance, jobId) {
        this.jobId = jobId;

        this.ok = function () {
            $uibModalInstance.close(this.jobId);
        };

        this.cancel = function () {
            $uibModalInstance.dismiss('cancel');
        };
    });
