'use strict';

//import MainComponent from './main.component';

import MainController from './main.controller';
import mainTpl from './main.html';

const mainPageModule = angular
  .module('main-module', ['ui.router'])
  .config(($stateProvider, $urlRouterProvider) => {
    'ngInject';

    $urlRouterProvider.otherwise('/analyzers');

    $stateProvider.state('main', {
      abstract: true,
      url: '/',
      component: 'main',
      resolve: {
        currentUser: ($q, $state, AuthService) => {
          'ngInject';

          let deferred = $q.defer();

          AuthService.current()
            .then(userData => deferred.resolve(userData))
            .catch(err =>
              deferred.resolve(err.status === 520 ? err.status : null)
            );

          return deferred.promise;
        }
      }
    });
  })
  .component('main', {
    controller: MainController,
    templateUrl: mainTpl,
    bindings: {
      currentUser: '<'
    }
  });
//.component('main', new MainComponent());

export default mainPageModule;
