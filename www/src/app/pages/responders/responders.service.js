'use strict';

import _ from 'lodash';

// import AnalyzerRunController from './analyzer.run.controller';
// import runAnalyzerModalTpl from './analyzer.run.modal.html';

export default class ResponderService {
  constructor($log, $q, $http, $uibModal) {
    'ngInject';

    this.$log = $log;
    this.$q = $q;
    this.$http = $http;
    this.$uibModal = $uibModal;

    this.responderDefinitions = null;
    this.responders = null;
    this.dataTypes = {};
  }

  getTypes() {
    return this.dataTypes;
  }

  definitions(force) {
    let defered = this.$q.defer();

    if (force || this.responderDefinitions === null) {
      this.$http.get('./api/responderdefinition').then(
        response => {
          this.responderDefinitions = _.keyBy(response.data, 'id');

          defered.resolve(this.responderDefinitions);
        },
        response => {
          defered.reject(response);
        }
      );
    } else {
      defered.resolve(this.responderDefinitions);
    }

    return defered.promise;
  }

  scan() {
    let defer = this.$q.defer();

    this.$http
      .post('./api/responderdefinition/scan', {})
      .then(response => defer.resolve(response.data))
      .catch(err => defer.reject(err));

    return defer.promise;
  }

  list() {
    let defered = this.$q.defer();

    this.$http
      .get('./api/responder', {
        params: {
          range: 'all',
          sort: '+name'
        }
      })
      .then(
        response => {
          this.responders = response.data;
          this.dataTypes = _.sortBy(
            _.uniq(_.flatten(_.map(response.data, 'dataTypeList')))
          );

          defered.resolve(response.data);
        },
        response => {
          defered.reject(response);
        }
      );

    return defered.promise;
  }

  configurations() {
    let defer = this.$q.defer();
    this.$http
      .get('./api/responderconfig')
      .then(response => defer.resolve(response.data), err => defer.reject(err));

    return defer.promise;
  }

  getBaseConfig(baseConfig) {
    let defer = this.$q.defer();

    if (baseConfig) {
      this.getConfiguration(baseConfig).then(
        cfg => defer.resolve(cfg),
        () => defer.resolve({})
      );
    } else {
      defer.resolve({});
    }

    return defer.promise;
  }

  getConfiguration(name) {
    let defer = this.$q.defer();

    this.$http
      .get(`./api/responderconfig/${name}`)
      .then(response => defer.resolve(response.data), err => defer.reject(err));

    return defer.promise;
  }

  saveConfiguration(name, values) {
    let defer = this.$q.defer();

    this.$http
      .patch(`./api/responderconfig/${name}`, values)
      .then(response => defer.resolve(response.data), err => defer.reject(err));

    return defer.promise;
  }

  // openRunModal(analyzers, observable) {
  //   let modalInstance = this.$uibModal.open({
  //     animation: true,
  //     templateUrl: runAnalyzerModalTpl,
  //     controller: AnalyzerRunController,
  //     controllerAs: '$modal',
  //     size: 'lg',
  //     resolve: {
  //       observable: () => angular.copy(observable),
  //       analyzers: () => angular.copy(analyzers)
  //     }
  //   });

  //   return modalInstance.result.then(result =>
  //     this.$q.all(
  //       result.analyzerIds.map(analyzerId =>
  //         this.run(analyzerId, result.observable)
  //       )
  //     )
  //   );
  // }

  // run(id, artifact) {
  //   let postData;

  //   if (artifact.dataType === 'file') {
  //     postData = {
  //       attachment: artifact.attachment,
  //       dataType: artifact.dataType,
  //       tlp: artifact.tlp
  //     };

  //     return this.$http({
  //       method: 'POST',
  //       url: './api/analyzer/' + id + '/run',
  //       headers: {
  //         'Content-Type': undefined
  //       },
  //       transformRequest: data => {
  //         let formData = new FormData(),
  //           copy = angular.copy(data, {}),
  //           _json = {};

  //         angular.forEach(data, (value, key) => {
  //           if (
  //             Object.getPrototypeOf(value) instanceof Blob ||
  //             Object.getPrototypeOf(value) instanceof File
  //           ) {
  //             formData.append(key, value);
  //             delete copy[key];
  //           } else {
  //             _json[key] = value;
  //           }
  //         });

  //         formData.append('_json', angular.toJson(_json));

  //         return formData;
  //       },
  //       data: postData
  //     });
  //   } else {
  //     postData = {
  //       data: artifact.data,
  //       attributes: {
  //         dataType: artifact.dataType,
  //         tlp: artifact.tlp
  //       }
  //     };

  //     return this.$http.post('./api/analyzer/' + id + '/run', postData);
  //   }
  // }
}