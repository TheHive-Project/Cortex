'use strict';

export default class OrganizationService {
  constructor($log, $q, $http) {
    'ngInject';

    this.$log = $log;
    this.$q = $q;
    this.$http = $http;
  }

  list() {
    let defer = this.$q.defer();

    this.$http
      .get('./api/organization')
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  getById(id) {
    let defer = this.$q.defer();

    this.$http
      .get(`./api/organization/${id}`)
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  create(org) {
    let defer = this.$q.defer();

    this.$http
      .post('./api/organization', org)
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  disable(id) {
    return this.$http.delete(`./api/organization/${id}`);
  }

  enable(id) {
    return this.$http.patch(`./api/organization/${id}`, { status: 'Active' });
  }

  analyzers(id) {
    let defer = this.$q.defer();

    this.$http
      .get(`./api/organization/${id}/analyzer`)
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  enableAnalyzer(organizationId, analyzerId, config) {
    return this.$http.post(
      `./api/organization/${organizationId}/analyzer/${analyzerId}`,
      config
    );
  }
}
