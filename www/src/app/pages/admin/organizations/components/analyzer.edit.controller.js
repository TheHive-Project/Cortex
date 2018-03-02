'use strict';

import _ from 'lodash/core';

export default class AnalyzerEditController {
  constructor(
    $log,
    $uibModalInstance,
    definition,
    configuration,
    analyzer,
    mode
  ) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;
    this.mode = mode;
    this.definition = definition;
    this.configuration = configuration;
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
            item.defaultValue ||
            (item.multi ? [undefined] : undefined) ||
            (this.configuration.config || {})[item.name])
      );

      this.analyzer = analyzer;

      this.$log.log('Inial analyzer config', this.analyzer);
    }
  }

  save() {
    this.$uibModalInstance.close(this.analyzer);
  }
  cancel() {
    this.$uibModalInstance.dismiss('cancel');
  }
}
