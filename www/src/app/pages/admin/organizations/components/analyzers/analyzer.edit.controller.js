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
        jobCache: undefined,
        jobTimeout: undefined
      };

      _.forEach(this.definition.configurationItems, item => {
        const property = item.name,
          configValue = (this.configuration.config || {})[property];

        if(configValue !== undefined) {
          analyzer.configuration[property] = configValue;
        } else if (item.defaultValue !== undefined) {
          analyzer.configuration[property] = item.defaultValue;
        } else {
          analyzer.configuration[property] = item.multi ? [undefined] : undefined;
        }
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

      if (analyzer.jobCache === undefined) {
        analyzer.jobCache = this.globalConfig.config.jobCache;
      }

      if (analyzer.jobTimeout === undefined) {
        analyzer.jobTimeout = this.globalConfig.config.jobTimeout;
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
