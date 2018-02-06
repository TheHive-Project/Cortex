'use strict';

export default class JobService {
  constructor($http) {
    'ngInject';

    this.$http = $http;
  }

  list(params) {
    return this.$http.post('./api/job/_search', params);
  }

  report(jobId) {
    return this.$http.get('./api/job/' + jobId + '/report');
  }

  remove(jobId) {
    return this.$http.delete('./api/job/' + jobId);
  }
}
