(function() {
    'use strict';

    angular.module('cortex')
        .value('_', window._)
        .value('Tlps', [{
            key: 'WHITE',
            value: 0
        }, {
            key: 'GREEN',
            value: 1
        }, {
            key: 'AMBER',
            value: 2
        }, {
            key: 'RED',
            value: 3
        }])
        .service('HtmlSanitizer', function($sanitize) {
            var entityMap = {
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': '&quot;',
                "'": '&#39;',
                "/": '&#x2F;'
            };

            this.sanitize = function(str) {
                return $sanitize(String(str).replace(/[&<>"'\/]/g, function(s) {
                    return entityMap[s];
                }));
            };
        })
        .service('NotificationService', function($state, HtmlSanitizer, Notification) {
            this.success = function(message) {
                var sanitized = HtmlSanitizer.sanitize(message);

                return Notification.success(sanitized);
            };
            this.error = function(message) {
                var sanitized = HtmlSanitizer.sanitize(message);

                return Notification.error(sanitized);
            };
            this.log = function(message, type) {
                Notification[type || 'error'](HtmlSanitizer.sanitize(message));
            };
            this.handleError = function(moduleName, data, status) {
                if (status === 401) {
                    $state.go('login');
                } else if (status === 520) {
                    $state.go('maintenance');
                } else if (angular.isString(data) && data !== '') {
                    this.log(moduleName + ': ' + data, 'error');
                } else if (angular.isObject(data)) {
                    this.log(moduleName + ': ' + data.message, 'error');
                }
            };
        })
        .service('VersionSrv', function($q, $http) {
            this.get = function() {
                var deferred = $q.defer();

                $http.get('./api/status').then(function(response) {
                    deferred.resolve(response);
                }, function(rejection) {
                    deferred.reject(rejection);
                });
                return deferred.promise;
            }

        })
        .service('AnalyzerSrv', function($q, $http) {
            var self = this;

            this.analyzers = null;
            this.dataTypes = {};

            this.getTypes = function() {
                return this.dataTypes;
            };

            this.list = function() {
                var defered = $q.defer();

                if (this.analyzers === null) {
                    $http.get('./api/analyzer')
                        .then(function(response) {
                            self.analyzers = response.data;

                            self.dataTypes = _.mapObject(
                                _.groupBy(
                                    _.flatten(
                                        _.pluck(response.data, 'dataTypeList')
                                    ),
                                    function(item) {
                                        return item;
                                    }
                                ),
                                function(value /*, key*/ ) {
                                    return value.length;
                                }
                            );

                            defered.resolve(response.data);
                        }, function(response) {
                            defered.reject(response);
                        });
                } else {
                    defered.resolve(this.analyzers);
                }

                return defered.promise;
            };

            this.run = function(id, artifact) {
                var postData;

                if (artifact.dataType === 'file') {
                    postData = {
                        data: artifact.attachment,
                        dataType: artifact.dataType,
                        tlp: artifact.tlp.value
                    };

                    return $http({
                        method: 'POST',
                        url: './api/analyzer/' + id + '/run',
                        headers: {
                            'Content-Type': undefined
                        },
                        transformRequest: function(data) {
                            var formData = new FormData(),
                                copy = angular.copy(data, {}),
                                _json = {};

                            angular.forEach(data, function(value, key) {
                                if (Object.getPrototypeOf(value) instanceof Blob || Object.getPrototypeOf(value) instanceof File) {
                                    formData.append(key, value);
                                    delete copy[key];
                                } else {
                                    _json[key] = value;
                                }
                            });

                            formData.append("_json", angular.toJson(_json));

                            return formData;
                        },
                        data: postData

                    });
                } else {
                    postData = {
                        data: artifact.data,
                        attributes: {
                            dataType: artifact.dataType,
                            tlp: artifact.tlp.value
                        }
                    };

                    return $http.post('./api/analyzer/' + id + '/run', postData);
                }
            };

        })
        .service('JobSrv', function($http) {
            this.list = function(params) {
                return $http.get('./api/job', {
                    params: params
                });
            };

            this.report = function(jobId) {
                return $http.get('./api/job/' + jobId + '/report');
            };

            this.remove = function(jobId) {
                return $http.delete('./api/job/' + jobId);
            };
        })
        .service('UtilsSrv', function() {
            this.sensitiveTypes = ['url', 'ip', 'mail', 'domain', 'filename'];

            this.fangValue = function(value) {
                return value
                    .replace(/\[\.\]/g, ".")
                    .replace(/hxxp/gi, "http")
                    .replace(/\./g, "[.]")
                    .replace(/http/gi, "hxxp");
            };

            this.fang = function(observable) {
                if (this.sensitiveTypes.indexOf(observable.dataType) === -1) {
                    return observable.data;
                }

                return this.fangValue(observable.data);
            };

            this.unfang = function(observable) {
                return observable.data
                    .replace(/\[\.\]/g, ".")
                    .replace(/hxxp/gi, "http");
            };
        })
        .filter('fang', function(UtilsSrv) {
            return function(value) {
                if (!value) {
                    return '';
                }

                return UtilsSrv.fangValue(value);
            };
        });
})();
