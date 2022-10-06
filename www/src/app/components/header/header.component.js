'use strict';

import _ from 'lodash/core';

import headerTpl from './header.html';

import AboutController from '../about/about.controller';
import aboutTpl from '../about/about.html';

import './header.scss';

class HeaderController {
  constructor(
    $state,
    $log,
    $uibModal,
    $scope,
    AuthService,
    AnalyzerService,
    NotificationService,
    AlertService
  ) {
    'ngInject';

    this.$state = $state;
    this.$log = $log;
    this.$uibModal = $uibModal;
    this.$scope = $scope;

    this.AuthService = AuthService;
    this.AnalyzerService = AnalyzerService;
    this.NotificationService = NotificationService;
    this.AlertService = AlertService;
  }

  logout() {
    this.AuthService.logout()
      .then(() => {
        this.$state.go('login');
      })
      .catch((data, status) => {
        this.NotificationService.error('AppCtrl', data, status);
      });
  }

  $onInit() {
    this.isOrgAdmin = this.AuthService.isOrgAdmin(this.main.currentUser);
    this.isSuperAdmin = this.AuthService.isSuperAdmin(this.main.currentUser);

    this.AlertService.startUpdate();
    this.$scope.$on('$destroy', () => {
      this.AlertService.stopUpdate();
    });
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
              .data || resp.data.attachment.name}`
          );
        });
      })
      .catch(err => {
        if (!_.isString(err)) {
          this.NotificationService.error(
            err.data.message ||
            `An error occurred: ${err.statusText}` ||
            'An unexpected error occurred'
          );
        }
      });
  }

  about() {
    this.$uibModal.open({
      controller: AboutController,
      controllerAs: '$modal',
      templateUrl: aboutTpl,
      resolve: {
        config: () => this.main.config
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
