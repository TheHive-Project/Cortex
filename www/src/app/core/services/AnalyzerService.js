'use strict';

import _ from 'lodash';

export default function(app) {
  app.service('AnalyzerService', function($q, $http) {
    this.analyzerDefinitions = null;
    this.analyzers = null;
    this.dataTypes = {};

    this.getTypes = function() {
      return this.dataTypes;
    };

    this.definitions = function() {
      let defered = $q.defer();

      if (this.analyzerDefinitions === null) {
        $http.get('./api/analyzerdefinition').then(
          response => {
            this.analyzerDefinitions = _.keyBy(response.data, 'id');

            defered.resolve(this.analyzerDefinitions);
          },
          response => {
            defered.reject(response);
          }
        );
      } else {
        defered.resolve(this.analyzerDefinitions);
      }

      return defered.promise;
    };

    this.list = function() {
      let defered = $q.defer();

      if (this.analyzers === null) {
        $http.get('./api/analyzer').then(
          response => {
            this.analyzers = response.data;

            this.dataTypes = _.mapValues(
              _.groupBy(_.flatten(_.map(response.data, 'dataTypeList'))),
              value => value.length
            );

            defered.resolve(response.data);
          },
          response => {
            defered.reject(response);
          }
        );
      } else {
        defered.resolve(this.analyzers);
      }

      return defered.promise;
    };

    this.run = function(id, artifact) {
      let postData;

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
          transformRequest: data => {
            let formData = new FormData(),
              copy = angular.copy(data, {}),
              _json = {};

            angular.forEach(data, (value, key) => {
              if (
                Object.getPrototypeOf(value) instanceof Blob ||
                Object.getPrototypeOf(value) instanceof File
              ) {
                formData.append(key, value);
                delete copy[key];
              } else {
                _json[key] = value;
              }
            });

            formData.append('_json', angular.toJson(_json));

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
  });
}
