'use strict';

import angular from 'angular';
import MaintenanceComponent from './maintenance.component';
import MaintenanceService from './maintenance.service';

const maintenanceModule = angular
  .module('thkit.maintenanceModule', [])
  .config($stateProvider => {
    'ngInject';

    $stateProvider.state('maintenance', {
      url: '/maintenance',
      component: 'maintenance'
    });
  })
  .component('maintenance', new MaintenanceComponent())
  .provider('MaintenanceService', MaintenanceService);

export default maintenanceModule;
