'use strict';

import UsersListController from './common/user-list/users-list.controller';
import UsersListTpl from './common/user-list/users-list.html';

import adminOrganizationsModule from './organizations/organizations.module';
import adminUsersModule from './users/users.module';

const adminModule = angular.module('cortex.admin', [
  adminOrganizationsModule.name,
  adminUsersModule.name
]);

adminModule.component('usersList', {
  controller: UsersListController,
  templateUrl: UsersListTpl,
  bindings: {
    organization: '<',
    users: '<',
    onReload: '&'
  },
  require: {
    main: '^^mainPage'
  }
});

export default adminModule;
