'use strict';

export default function(app) {
  app.service('JobService', function($q, $http) {
    'ngInject';

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
  });
}
