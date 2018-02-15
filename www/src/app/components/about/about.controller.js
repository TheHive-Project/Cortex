'use strict';

export default class AboutController {
  constructor($log, $uibModalInstance, config) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;

    this.version = config.versions;
    this.connectors = config.connectors;
  }

  close() {
    this.$uibModalInstance.close();
  }
}
