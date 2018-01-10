'use strict';

import MaintenanceController from './maintenance.controller';
import maintenanceTpl from './maintenance.html';

export default class MigrationComponent {
  constructor() {
    this.controller = MaintenanceController;
    this.templateUrl = maintenanceTpl;
  }
}
