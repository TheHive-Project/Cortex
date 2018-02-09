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
    if (_.isEmpty(this.analyzer)) {
      let analyzer = {
        name: this.definition.id,
        configuration: {},
        rate: undefined,
        rateUnit: undefined
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
    this.$uibModalInstance.close(this.analyzer);
  }
  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
}
