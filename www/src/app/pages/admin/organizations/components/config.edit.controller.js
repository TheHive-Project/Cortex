'use strict';

export default class ConfigurationEditController {
  constructor($log, $uibModalInstance, configuration) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;
    this.configuration = configuration;
  }

  save() {
    this.$uibModalInstance.close(this.configuration.config);
  }
  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
}
