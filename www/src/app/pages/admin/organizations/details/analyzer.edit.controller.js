'use strict';

import _ from 'lodash/core';

export default class AnalyzerEditController {
  constructor($log, $uibModalInstance, definition, analyzer, mode) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;
    this.mode = mode;
    this.definition = definition;
    this.analyzer = analyzer;
  }

  $onInit() {
    this.$log.log('onInit of AnalyzerEditController', this.analyzer);

    if (_.isEmpty(this.analyzer)) {
      let analyzer = {
        name: this.definition.id,
        configuration: {},
        rate: null,
        rateUnit: null
      };

      _.forEach(
        this.definition.configurationItems,
        item =>
          (analyzer.configuration[item.name] =
            item.defaultValue || (item.multi ? [] : undefined))
      );

      this.analyzer = analyzer;
    }
  }

  save() {
    this.$log.log('Save clicked');
    this.$uibModalInstance.close(this.analyzer);
  }
  cancel() {
    this.$log.log('Cancel clicked');
    this.$uibModalInstance.dismiss('cancel');
  }
}
