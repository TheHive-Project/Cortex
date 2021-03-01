'use strict';

import lo from 'lodash';
import angular from 'angular';

export default function (app) {
  function StreamSrv(
    $rootScope,
    $http,
    $log,
    NotificationService /* , UserSrv, AuthService, AfkSrv */
  ) {
    'ngInject';

    this.isPolling = false;
    this.streamId = null;

    this.init = function () {
      this.streamId = null;
      this.requestStream();
    };
    this.runCallbacks = function (id, objectType, message) {
      $rootScope.$broadcast('stream:' + id + '-' + objectType, message);
    };

    this.handleStreamResponse = function (data) {
      if (!data || data.length === 0) {
        return;
      }

      let byRootIds = {};
      let byObjectTypes = {};
      let byRootIdsWithObjectTypes = {};
      let bySecondaryObjectTypes = {};

      angular.forEach(data, message => {
        let rootId = message.base.rootId;
        let objectType = message.base.objectType;
        let rootIdWithObjectType = rootId + '|' + objectType;
        let secondaryObjectTypes = message.summary ?
          lo.without(lo.keys(message.summary), objectType) : [];

        if (rootId in byRootIds) {
          byRootIds[rootId].push(message);
        } else {
          byRootIds[rootId] = [message];
        }

        if (objectType in byObjectTypes) {
          byObjectTypes[objectType].push(message);
        } else {
          byObjectTypes[objectType] = [message];
        }

        if (rootIdWithObjectType in byRootIdsWithObjectTypes) {
          byRootIdsWithObjectTypes[rootIdWithObjectType].push(message);
        } else {
          byRootIdsWithObjectTypes[rootIdWithObjectType] = [message];
        }

        angular.forEach(secondaryObjectTypes, type => {
          if (type in bySecondaryObjectTypes) {
            bySecondaryObjectTypes[type].push(message);
          } else {
            bySecondaryObjectTypes[type] = [message];
          }
        });
      });

      angular.forEach(byRootIds, (messages, rootId) => {
        this.runCallbacks(rootId, 'any', messages);
      });
      angular.forEach(byObjectTypes, (messages, objectType) => {
        this.runCallbacks('any', objectType, messages);
      });

      // Trigger strem event for sub object types
      angular.forEach(bySecondaryObjectTypes, (messages, objectType) => {
        this.runCallbacks('any', objectType, messages);
      });

      angular.forEach(
        byRootIdsWithObjectTypes,
        (messages, rootIdWithObjectType) => {
          let temp = rootIdWithObjectType.split('|', 2),
            rootId = temp[0],
            objectType = temp[1];

          this.runCallbacks(rootId, objectType, messages);
        }
      );

      this.runCallbacks('any', 'any', data);
    };

    this.poll = function () {
      // Skip polling is a poll is already running
      if (this.streamId === null || this.isPolling === true) {
        return;
      }

      // Flag polling start
      this.isPolling = true;

      // Poll stream changes
      $http.get('./api/stream/' + this.streamId)
        .then(
          response => {
            // Flag polling end
            this.isPolling = false;

            // Handle stream data and callbacks
            this.handleStreamResponse(response.data);

            // Check if the session will expire soon
            // TODO
            // if (status === 220) {
            //   AfkSrv.prompt().then(function () {
            //     UserSrv.getUserInfo(AuthService.currentUser.id).then(
            //       function () {},
            //       function (response) {
            //         NotificationService.error('StreamSrv', response.data, response.status);
            //       }
            //     );
            //   });
            // }
            this.poll();
          })
        .catch(err => {
          // Initialize the stream;
          this.isPolling = false;

          if (err.status === 401) {
            return;
          }
          if (err.status !== 404) {
            NotificationService.error('StreamSrv', err.data, err.status);
          }

          this.init();
        });
    };

    this.requestStream = function () {
      if (this.streamId !== null) {
        return;
      }

      $http.post('./api/stream').then(
        response => {
          this.streamId = response.data;
          this.poll(this.streamId);
        },
        (data, status) => {
          NotificationService.error('StreamSrv', data, status);
        }
      );
    };

    /**
     * @param config {Object} This configuration object has the following attributes
     * <li>rootId</li>
     * <li>objectType {String}</li>
     * <li>scope {Object}</li>
     * <li>callback {Function}</li>
     */
    this.addListener = function (config) {
      if (!config.scope) {
        $log.error('No scope provided, use the old listen method', config);
        this.listen(config.rootId, config.objectType, config.callback);
        return;
      }

      let eventName = 'stream:' + config.rootId + '-' + config.objectType;

      config.scope.$on(eventName, (event, data) => {
        config.callback(data);
      });
    };
  }

  app.service('StreamSrv', StreamSrv);
}
