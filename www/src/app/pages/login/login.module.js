'use strict';

import LoginController from './login.controller';
import tpl from './login.html';

const loginPageModule = angular
  .module('login-module', ['ui.router'])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('login', {
      url: '/login',
      component: 'login'
    });
  })
  .component('login', {
    controller: LoginController,
    templateUrl: tpl
  });

export default loginPageModule;
