'use strict';

import LoginController from './login.controller';
import tpl from './login.page.html';

import './login.page.scss';

const loginPageModule = angular
  .module('login-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('login', {
      url: '/login',
      component: 'loginPage',
      resolve: {
        config: ($q, VersionService) => VersionService.get()
          .then(response => $q.resolve(response.data))
      },
      params: {
        autoLogin: false
      }
    });
  })
  .component('loginPage', {
    controller: LoginController,
    templateUrl: tpl,
    bindings: {
      config: '<'
    }
  });

export default loginPageModule;