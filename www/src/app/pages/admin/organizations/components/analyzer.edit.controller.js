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
          (analyzer.configuration[item.name] = (this.configuration.config ||
            {})[item.name]) ||
          item.defaultValue ||
          (item.multi ? [undefined] : undefined)
      );

      // Handle TLP default config
      const globalConfig = [
        'proxy_http',
        'proxy_https',
        'auto_extract_artifacts'
      ];
      _.forEach(globalConfig, cnf => {
        if (analyzer.configuration[cnf] === undefined) {
          analyzer.configuration[cnf] =
            this.configuration.config[cnf] || undefined;
        }
      });

      if (analyzer.configuration.check_tlp === undefined) {
        analyzer.configuration.check_tlp = true;
      }
      if (analyzer.configuration.max_tlp === undefined) {
        analyzer.configuration.max_tlp = 2;
      }

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
