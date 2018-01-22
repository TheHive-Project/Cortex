'use strict';

import _ from 'lodash/core';

import headerTpl from './header.html';
//import HeaderController from './header.controller';

import './header.scss';

class HeaderController {
  constructor(
    $state,
    $log,
    $q,
    $uibModal,
    AuthService,
    AnalyzerService,
    NotificationService
  ) {
    'ngInject';

    this.$state = $state;
    this.$log = $log;
    this.$uibModal = $uibModal;
    this.$q = $q;

    this.AuthService = AuthService;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;
  }

  logout() {
    this.$log.log('logout');
    this.AuthService.logout()
      .then(() => {
        this.$state.go('login');
      })
      .catch((data, status) => {
        this.NotificationService.error('AppCtrl', data, status);
      });
  }

  $onInit() {
    this.isAdmin = this.AuthService.isAdmin(this.main.currentUser);
  }

  newAnalysis() {
    this.AnalyzerService.list()
      .then(analyzers => this.AnalyzerService.openRunModal(analyzers, {}))
      .then(responses => {
        if (this.$state.is('main.jobs')) {
          this.$state.reload();
        } else {
          this.$state.go('main.jobs');
        }

        responses.forEach(resp => {
          this.NotificationService.success(
            `${resp.data.analyzerName} started successfully on ${resp.data
              .data || resp.data.attributes.filename}`
          );
        });
      })
      .catch(err => {
        this.$log.log(err);
        if (!_.isString(err)) {
          this.NotificationService.error(
            'An error occurred: ' + err.statusText ||
              'An unexpected error occurred'
          );
        }
      });
  }
}

export default class HeaderComponent {
  constructor() {
    this.templateUrl = headerTpl;
    this.controller = HeaderController;
    this.bindings = {
      currentUser: '<'
    };
    this.require = {
      main: '^mainPage'
    };
  }
}
