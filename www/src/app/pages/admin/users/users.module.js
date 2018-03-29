'use strict';

import angular from 'angular';

import UsersPageController from './list/users.page.controller';
import usersPageTpl from './list/users.page.html';

const usersModule = angular
  .module('users-module', ['ui.router'])
  .config(($stateProvider, Roles) => {
    'ngInject';
    $stateProvider.state('main.users', {
      url: 'admin/users',
      component: 'usersPage',
      resolve: {
        users: UserService => UserService.list()
      },
      data: {
        allow: [Roles.SUPERADMIN, Roles.ORGADMIN]
      }
    });
  })
  .component('usersPage', {
    controller: UsersPageController,
    templateUrl: usersPageTpl,
    bindings: {
      users: '<'
    }
  });

export default usersModule;
