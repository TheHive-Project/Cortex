'use strict';

import _ from 'lodash/core';

export default class AnalyzerEditController {
  constructor(
    $log,
    $uibModalInstance,
    definition,
    globalConfig,
    baseConfig,
    configuration,
    analyzer,
    mode
  ) {
    'ngInject';

    this.$log = $log;
    this.$uibModalInstance = $uibModalInstance;
    this.mode = mode;
    this.definition = definition;
    this.globalConfig = globalConfig;
    this.baseConfig = baseConfig;
    this.configuration = configuration;
    this.analyzer = analyzer;
  }

  $onInit() {
    if (_.isEmpty(this.analyzer)) {
      let analyzer = {
        name: this.definition.id,
        configuration: {},
        rate: undefined,
        rateUnit: undefined,
        jobCache: null
      };

      _.forEach(this.definition.configurationItems, item => {
        const property = item.name,
          configValue = (this.configuration.config || {})[property];

        analyzer.configuration[property] =
          configValue ||
          item.defaultValue ||
          (item.multi ? [undefined] : undefined);
      });

      // Handle TLP default config
      const globalConfig = [
        'proxy_http',
        'proxy_https',
        'auto_extract_artifacts',
        'cacerts'
      ];
      _.forEach(globalConfig, cnf => {
        if (analyzer.configuration[cnf] === undefined) {
          analyzer.configuration[cnf] =
            this.configuration.config[cnf] !== undefined ?
            this.configuration.config[cnf] :
            undefined;
        }
      });

      if (analyzer.configuration.check_tlp === undefined) {
        analyzer.configuration.check_tlp = true;
      }
      if (analyzer.configuration.max_tlp === undefined) {
        analyzer.configuration.max_tlp = 2;
      }
      if (analyzer.configuration.check_pap === undefined) {
        analyzer.configuration.check_pap = true;
      }
      if (analyzer.configuration.max_pap === undefined) {
        analyzer.configuration.max_pap = 2;
      }

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