'use strict';

import _ from 'lodash';

export default class ResponderService {
  constructor($log, $q, $http) {
    'ngInject';

    this.$log = $log;
    this.$q = $q;
    this.$http = $http;

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

          // Compute type (process/docker)
          _.keys(this.responderDefinitions).forEach(key => {
            let def = this.responderDefinitions[key];

            def.runners = [];

            if (def.command && def.command !== null) {
              def.runners.push('Process');
            }

            if (def.dockerImage && def.dockerImage !== null) {
              def.runners.push('Docker');
            }
          });

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
    return this.$http
      .post('./api/responderdefinition/scan', {})
      .then(response => this.$q.resolve(response.data))
      .catch(err => this.$q.reject(err));
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
    return this.$http
      .get('./api/responderconfig')
      .then(response => this.$q.resolve(response.data), err => this.$q.reject(err));
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
    return this.$http
      .get(`./api/responderconfig/${name}`)
      .then(response => {
        let cfg = response.data;

        if (name === 'global') {
          // Prepare the default values of the global config
          let globalWithDefaults = {};
          _.each(cfg.configurationItems, item => {
            globalWithDefaults[item.name] = item.defaultValue;
          });

          // Set the default value of the global config that are not set
          _.defaults(cfg.config, globalWithDefaults);
        }

        return this.$q.resolve(cfg);
      })
      .catch(err => this.$q.reject(err));
  }

  saveConfiguration(name, values) {
    return this.$http
      .patch(`./api/responderconfig/${name}`, values)
      .then(response => this.$q.resolve(response.data), err => this.$q.reject(err));
  }
}