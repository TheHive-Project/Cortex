'use strict';

//import MainComponent from './main.component';

import MainController from './main.controller';
import mainTpl from './main.page.html';

const mainPageModule = angular
  .module('main-module', ['ui.router'])
  .config(($stateProvider, $urlRouterProvider) => {
    'ngInject';

    $urlRouterProvider.otherwise('/index');

    $stateProvider.state('index', {
      url: '/index'
    });

    $stateProvider.state('main', {
      abstract: true,
      url: '/',
      component: 'mainPage',
      resolve: {
        currentUser: ($q, AuthService, NotificationService) => {
          'ngInject';

          let deferred = $q.defer();

          AuthService.current()
            .then(userData => deferred.resolve(userData))
            .catch(err => {
              NotificationService.handleError('Application', err.data, err.status);
              return deferred.reject(err)
            });

          return deferred.promise;
        },
        config: ($q, VersionService) => {
          let defer = $q.defer();

          VersionService.get().then(response => {
            defer.resolve(response.data);
          });

          return defer.promise;
        }
      }
    });
  })
  .component('mainPage', {
    controller: MainController,
    templateUrl: mainTpl,
    bindings: {
      currentUser: '<',
      config: '<'
    }
  });

export default mainPageModule;