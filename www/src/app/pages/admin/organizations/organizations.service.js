'use strict';

import _ from 'lodash/core';

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

  update(id, org) {
    let defer = this.$q.defer();

    this.$http
      .patch(`./api/organization/${id}`, _.pick(org, 'description', 'status'))
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  disable(id) {
    return this.$http.delete(`./api/organization/${id}`);
  }

  enable(id) {
    return this.$http.patch(`./api/organization/${id}`, {
      status: 'Active'
    });
  }

  analyzers() {
    let defer = this.$q.defer();

    this.$http
      .get(`./api/organization/analyzer`, {
        params: {
          range: 'all'
        }
      })
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  responders() {
    return this.$http
      .get(`./api/organization/responder`, {
        params: {
          range: 'all'
        }
      })
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
  }

  users(id) {
    let defer = this.$q.defer();

    this.$http
      .get(`./api/organization/${id}/user`)
      .then(response => defer.resolve(response))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  enableAnalyzer(analyzerId, config) {
    return this.$http.post(`./api/organization/analyzer/${analyzerId}`, config);
  }

  updateAnalyzer(analyzerId, config) {
    return this.$http.patch(`./api/analyzer/${analyzerId}`, config);
  }

  disableAnalyzer(analyzerId) {
    return this.$http.delete(`./api/analyzer/${analyzerId}`);
  }

  enableResponder(responderId, config) {
    return this.$http.post(`./api/organization/responder/${responderId}`, config);
  }

  updateResponder(responderId, config) {
    return this.$http.patch(`./api/responder/${responderId}`, config);
  }

  disableResponder(responderId) {
    return this.$http.delete(`./api/responder/${responderId}`);
  }
}