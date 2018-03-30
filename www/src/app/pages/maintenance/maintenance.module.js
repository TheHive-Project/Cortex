'use strict';

import angular from 'angular';
import MaintenanceService from './maintenance.service';
import MaintenanceController from './maintenance.controller';

import maintenanceTpl from './maintenance.page.html';

const maintenanceModule = angular
  .module('thkit.maintenanceModule', [])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('maintenance', {
      url: '/maintenance',
      component: 'maintenancePage'
    });
  })
  .component('maintenancePage', {
    controller: MaintenanceController,
    templateUrl: maintenanceTpl
  })
  .provider('MaintenanceService', MaintenanceService);

export default maintenanceModule;
