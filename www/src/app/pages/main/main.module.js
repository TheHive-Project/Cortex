'use strict';

import MainComponent from './main.component';

const mainPageModule = angular
  .module('main-module', ['ui.router'])
  .config(($stateProvider, $urlRouterProvider) => {
    'ngInject';

    $urlRouterProvider.otherwise('/');

    $stateProvider.state('main', {
      url: '/',
      component: 'main'
    });
  })
  .component('main', new MainComponent());

export default mainPageModule;
