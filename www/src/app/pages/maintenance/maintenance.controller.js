'use strict';

import _ from 'lodash';
import angular from 'angular';

export default class MaintenanceController {
  constructor(
    $log,
    $scope,
    $http,
    $state,
    NotificationService,
    MaintenanceService,
    StreamSrv,
    UserService
  ) {
    'ngInject';
    this.$log = $log;
    this.$scope = $scope;
    this.$http = $http;
    this.$state = $state;
    this.NotificationService = NotificationService;
    this.StreamSrv = StreamSrv;
    this.UserService = UserService;

    this.migrationStatus = {};
    this.showUserForm = false;
    this.migrating = false;
    this.newUser = {};

    this.successState = MaintenanceService.getSuccessState();
  }

  migrate() {
    this.migrating = true;
    this.$http
      .post(
        './api/maintenance/migrate',
        {},
        {
          timeout: 10 * 60 * 60 * 1000 // 10 minutes
        }
      )
      .then(
        () => {
          this.$log.log('Migration started');
        },
        err => {
          if (angular.isObject(err)) {
            this.NotificationService.error(
              'UserMgmtCtrl',
              err.data,
              err.status
            );
          } else {
            this.$log.error('Migration timeout');
          }
        }
      );
  }

  createInitialUser() {
    this.$log.log('createInitialUser');
    this.UserService.save(
      {
        login: angular.lowercase(this.newUser.login),
        name: this.newUser.name,
        password: this.newUser.password,
        roles: ['read', 'write', 'admin'],
        organization: 'default'
      },
      () => {
        this.$state.go(this.successState);
      }
    );
  }

  $onInit() {
    this.$log.debug('MaintenanceController.$onInit() called');

    this.StreamSrv.init();

    this.StreamSrv.addListener({
      scope: this.$scope,
      rootId: 'any',
      objectType: 'migration',
      callback: events => {
        angular.forEach(events, event => {
          let tableName = event.base.tableName;

          if (tableName === 'end') {
            // check if there is at least one user registered
            this.UserService.query(
              users => {
                if (users.length === 0) {
                  this.showUserForm = true;
                } else {
                  this.$state.go(this.successState);
                }
              },
              () => {
                this.$state.go(this.successState);
              }
            );
          }
          let current = 0;

          if (angular.isDefined(this.migrationStatus[tableName])) {
            current = this.migrationStatus[tableName].current;
          }
          if (event.base.current > current) {
            this.migrationStatus[tableName] = event.base;
          }
        });
      }
    });
  }
}
