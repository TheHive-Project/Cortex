'use strict';

import headerTpl from './header.html';
//import HeaderController from './header.controller';

import './header.scss';

class HeaderController {
  constructor($state, $log, AuthService, NotificationService) {
    'ngInject';

    this.$state = $state;
    this.$log = $log;

    this.AuthService = AuthService;
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
